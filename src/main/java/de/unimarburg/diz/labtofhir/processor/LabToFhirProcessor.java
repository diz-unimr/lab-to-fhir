package de.unimarburg.diz.labtofhir.processor;

import de.unimarburg.diz.labtofhir.mapper.MiiLabReportMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import java.util.function.Function;
import org.apache.kafka.streams.kstream.KStream;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class LabToFhirProcessor {

    private final MiiLabReportMapper reportMapper;

    @Autowired
    public LabToFhirProcessor(MiiLabReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    @Bean
    public Function<KStream<String, LaboratoryReport>, KStream<String, Bundle>> process() {

        return report -> report
            .mapValues(reportMapper)
            .filter((k, v) -> v != null);
    }
}
