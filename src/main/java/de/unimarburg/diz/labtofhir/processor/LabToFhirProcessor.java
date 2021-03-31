package de.unimarburg.diz.labtofhir.processor;

import de.unimarburg.diz.FhirPseudonymizer;
import de.unimarburg.diz.labtofhir.mapper.MiiLabReportMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.MappingContainer;
import de.unimarburg.diz.labtofhir.model.MappingResult;
import java.util.Arrays;
import java.util.function.Function;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Predicate;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class LabToFhirProcessor {

    private static final Logger log = LoggerFactory.getLogger(LabToFhirProcessor.class);
    private final MiiLabReportMapper fhirMapper;
    private final FhirPseudonymizer fhirPseudonymizer;

    private final Predicate<String, MappingContainer<LaboratoryReport, Bundle>> success = (k, v) ->
        v.getResultType() == MappingResult.SUCCESS;
    private final Predicate<String, MappingContainer<LaboratoryReport, Bundle>> missingCode = (k, v) ->
        v.getResultType() == MappingResult.MISSING_CODE_MAPPING;

    @Autowired
    public LabToFhirProcessor(MiiLabReportMapper fhirMapper, FhirPseudonymizer fhirPseudonymizer) {
        this.fhirMapper = fhirMapper;
        this.fhirPseudonymizer = fhirPseudonymizer;
    }

    @SuppressWarnings("unchecked")
    @Bean
    public Function<KTable<String, LaboratoryReport>, KStream<String, Bundle>[]> process() {

        return report -> {

            var branches = report.
                mapValues(fhirMapper)
                .filter((k, v) -> v.getException() == null)
                .mapValues(x -> x.setValue(fhirPseudonymizer.process(x.getValue())))
                .toStream()
                .selectKey((k, v) -> v.getValue().getId())
                .branch(success, missingCode);

            return Arrays.stream(branches)
                .map(s -> s.map((k, v) -> new KeyValue<>(v.getValue().getId(), v.getValue())))
                .toArray(KStream[]::new);
        };
    }

}
