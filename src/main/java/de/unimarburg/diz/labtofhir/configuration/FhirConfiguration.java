package de.unimarburg.diz.labtofhir.configuration;

import ca.uhn.fhir.context.FhirContext;
import org.apache.kafka.common.serialization.Serde;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.miracum.kafka.serializers.KafkaFhirSerde;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FhirConfiguration {

    @Bean
    public Serde<IBaseResource> fhirSerde() {
        return new KafkaFhirSerde();
    }

    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    @Autowired
    public FhirProperties fhirProperties(
        @Value("${fhir.systems.serviceRequestId}") String serviceRequestIdSystem,
        @Value("${fhir.systems.diagnosticReportId}") String diagnosticReportSystem,
        @Value("${fhir.systems.observationId}") String observationIdSystem,
        @Value("${fhir.systems.patientId}") String patientIdSystem,
        @Value("${fhir.systems.encounterId}") String encounterIdSystem,
        @Value("${fhir.systems.assignerId}") String assignerIdSystem,
        @Value("${fhir.systems.assignerCode}") String assignerIdCode,
        @Value("${fhir.generateNarrative}") Boolean generateNarrative) {

        return new FhirProperties(serviceRequestIdSystem, diagnosticReportSystem,
            observationIdSystem, patientIdSystem, encounterIdSystem, assignerIdSystem,
            assignerIdCode, generateNarrative);
    }
}

