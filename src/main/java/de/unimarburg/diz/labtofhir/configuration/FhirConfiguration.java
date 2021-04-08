package de.unimarburg.diz.labtofhir.configuration;

import ca.uhn.fhir.context.FhirContext;
import de.unimarburg.diz.FhirPseudonymizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
public class FhirConfiguration {


    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();
    }

    @Bean
    public FhirProperties fhirProperties() {
        return new FhirProperties();
    }

    @Bean
    @Autowired
    public FhirPseudonymizer pseudonymizer(FhirContext fhirContext,
        @Value("${services.pseudonymizer.url}") String pseudonymizerUrl) {
        return new FhirPseudonymizer(fhirContext, pseudonymizerUrl);
    }

}

