package de.unimarburg.diz.labtofhir.configuration;

import ca.uhn.fhir.context.FhirContext;
import org.apache.kafka.common.serialization.Serde;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.miracum.kafka.serializers.KafkaFhirSerde;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
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

    public FhirProperties fhirProperties() {
        return new FhirProperties();
    }
}

