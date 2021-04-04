package de.unimarburg.diz.labtofhir.processor;

import de.unimarburg.diz.FhirPseudonymizer;
import de.unimarburg.diz.labtofhir.mapper.MiiLabReportMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.MappingContainer;
import de.unimarburg.diz.labtofhir.model.MappingResult;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Predicate;
import org.apache.kafka.streams.kstream.Produced;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.support.KafkaStreamBrancher;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Service;

@Service
public class LabToFhirProcessor {

    private static final Logger log = LoggerFactory.getLogger(LabToFhirProcessor.class);
    private final MiiLabReportMapper fhirMapper;
    private final FhirPseudonymizer fhirPseudonymizer;

    private final Predicate<String, MappingContainer<LaboratoryReport, Bundle>> success = (k, v) ->
        v.getResultType() == MappingResult.SUCCESS;
    private final Predicate<String, MappingContainer<LaboratoryReport, Bundle>> error = (k, v) ->
        v.getResultType() == MappingResult.EXCEPTION;
    private final String outputTopic;
    private final String errorTopic;

    @Autowired
    public LabToFhirProcessor(MiiLabReportMapper fhirMapper, FhirPseudonymizer fhirPseudonymizer,
        @Value("${spring.cloud.stream.bindings.process-out-0.destination}") String outputTopic,
        @Value("${spring.cloud.stream.bindings.process-out-0.error}") String errorTopic) {
        this.fhirMapper = fhirMapper;
        this.fhirPseudonymizer = fhirPseudonymizer;
        this.outputTopic = outputTopic;
        this.errorTopic = errorTopic;
    }

    //    @SuppressWarnings("unchecked")
    @Bean
    public Function<KTable<String, LaboratoryReport>, KStream<String, Bundle>> process() {

        return report -> {

            var stream = report.
                mapValues(fhirMapper)
//                .filter((k, v) -> v.getException() == null)
                .mapValues(x -> x.setValue(fhirPseudonymizer.process(x.getValue())))
                .toStream();
//            var branches = stream.branch(success, error);

            return new KafkaStreamBrancher<String, MappingContainer<LaboratoryReport, Bundle>>()
                .branch(error,
                    ks -> ks.map(
                        (k, v) -> new KeyValue<>(v.getSource().getId(),
                            v.getSource()
                        ))
                        .to(errorTopic,
                            Produced.with(Serdes.Integer(), new JsonSerde<>())))

//                .defaultBranch(ks -> ks.to(outputTopic))
                .onTopOf(stream)
                .map((k, v) -> new KeyValue<>(v.getValue().getId(), v.getValue()));

//            return Arrays.stream(branches)
//                .map(s -> s.map((k, v) -> new KeyValue<>(v.getValue().getId(), v.getValue())))
//                .toArray(KStream[]::new);
        };
    }

}
