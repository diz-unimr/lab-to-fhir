package de.unimarburg.diz.labtofhir.processor;

import de.unimarburg.diz.labtofhir.mapper.LabReportToFhirMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import java.util.function.Function;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class LabToFhirProcessor {

    private static final Logger log = LoggerFactory.getLogger(LabToFhirProcessor.class);
    private final LabReportToFhirMapper fhirMapper;

    @Autowired
    public LabToFhirProcessor(LabReportToFhirMapper fhirMapper) {
        this.fhirMapper = fhirMapper;
    }

    @Bean
    public Function<KTable<String, LaboratoryReport>, KStream<String, Bundle>> process() {
        return report -> report.
            mapValues(fhirMapper)
            .toStream()
            .filter((key, value) -> value != null)
            .selectKey((k, v) -> v.getId());
    }
}
