package de.unimarburg.diz.labtofhir.processor;

import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.mapper.LoincMapper;
import de.unimarburg.diz.labtofhir.mapper.MiiLabReportMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.LoincMap;
import de.unimarburg.diz.labtofhir.serializer.FhirSerde;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class LabToFhirProcessor {

    private final MiiLabReportMapper fhirMapper;
    private final LoincMapper loincMapper;
    private final FhirProperties fhirProperties;

    @Autowired
    public LabToFhirProcessor(MiiLabReportMapper fhirMapper, LoincMapper loincMapper,
        FhirProperties fhirProperties) {
        this.fhirMapper = fhirMapper;
        this.loincMapper = loincMapper;
        this.fhirProperties = fhirProperties;
    }

    @Bean
    public BiFunction<KStream<String, LaboratoryReport>, KTable<String, LoincMap>, KStream<String, Bundle>> process() {

        return (report, loincMap) -> report
            .mapValues(fhirMapper)
            .filter((k, v) -> v != null)
            .flatMap(this::splitEntries)
            .toTable(Named.as("entries"),
                Materialized.with(Serdes.String(), new FhirSerde<>(Bundle.class)))
            .leftJoin(loincMap, this.swlCodeExtractor(fhirProperties), loincMapper)
            .toStream();
    }

    private List<KeyValue<String, Bundle>> splitEntries(String key, Bundle bundle) {
        return bundle
            .getEntry()
            .stream()
            .map(e -> new KeyValue<>(e
                .getResource()
                .getId(), new Bundle()
                .setType(BundleType.BATCH)
                .setEntry(List.of(e))))
            .toList();
    }

    private Function<Bundle, String> swlCodeExtractor(FhirProperties fhirProperties) {

        return bundle -> {
            if (bundle
                .getEntryFirstRep()
                .getResource() instanceof Observation obs) {
                return obs
                    .getCode()
                    .getCoding()
                    .stream()
                    .filter(x -> StringUtils.equals(fhirProperties
                        .getSystems()
                        .getLaboratorySystem(), x.getSystem()))
                    .findAny()
                    .map(Coding::getCode)
                    .orElse(UUID
                        .randomUUID()
                        .toString());
            }
            return UUID
                .randomUUID()
                .toString();
        };
    }
}
