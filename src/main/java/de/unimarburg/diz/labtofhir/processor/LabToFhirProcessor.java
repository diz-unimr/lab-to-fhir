package de.unimarburg.diz.labtofhir.processor;

import de.unimarburg.diz.labtofhir.mapper.AimLabMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import java.util.function.Function;
import org.apache.kafka.streams.kstream.KStream;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class LabToFhirProcessor {

    private final AimLabMapper reportMapper;

    @Autowired
    public LabToFhirProcessor(AimLabMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Bean
    public Function<KStream<String, LaboratoryReport>, KStream<String, Bundle>> process() {

        return report -> report.mapValues(reportMapper)
            .filter((k, v) -> v != null);
    }
}
