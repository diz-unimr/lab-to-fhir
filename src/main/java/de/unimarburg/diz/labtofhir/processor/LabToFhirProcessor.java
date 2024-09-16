package de.unimarburg.diz.labtofhir.processor;

import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.mapper.AimLabMapper;
import de.unimarburg.diz.labtofhir.mapper.Hl7LabMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import org.apache.kafka.streams.kstream.KStream;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
public class LabToFhirProcessor {

    private final AimLabMapper aimMapper;
    private final Hl7LabMapper hl7Mapper;

    @Autowired
    public LabToFhirProcessor(AimLabMapper reportMapper,
                              Hl7LabMapper hl7Mapper) {
        this.aimMapper = reportMapper;
        this.hl7Mapper = hl7Mapper;
    }

    @Bean
    public Function<KStream<String, LaboratoryReport>, KStream<String,
        Bundle>> aim() {

        return report -> report.mapValues(aimMapper)
            .filter((k, v) -> v != null);
    }

    @Bean
    public Function<KStream<String, ORU_R01>, KStream<String, Bundle>> hl7() {

        return report -> report.mapValues(hl7Mapper)
            .filter((k, v) -> v != null);
    }
}
