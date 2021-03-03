package de.unimarburg.diz.labtofhir.configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.unimarburg.diz.labtofhir.serializer.DiagnosticReportDeserializer;
import de.unimarburg.diz.labtofhir.serializer.InstantDeserializer;
import java.time.Instant;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfiguration {

    @Bean
    @Primary
    public static ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false);
        var module = new SimpleModule();
        module.addDeserializer(DiagnosticReport.class, new DiagnosticReportDeserializer());
        module.addDeserializer(Instant.class, new InstantDeserializer());

        mapper.registerModule(module);

        return mapper;
    }
}
