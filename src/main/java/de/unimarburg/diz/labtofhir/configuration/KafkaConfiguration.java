package de.unimarburg.diz.labtofhir.configuration;

import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.serializer.FhirSerde;
import de.unimarburg.diz.labtofhir.serializer.Hl7Deserializer;
import de.unimarburg.diz.labtofhir.serializer.Hl7Serializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.retry.annotation.EnableRetry;

@SuppressWarnings("checkstyle:LineLength")
@Configuration
@EnableKafka
@EnableRetry
public class KafkaConfiguration {

    private static final Logger LOG =
        LoggerFactory.getLogger(KafkaConfiguration.class);
    private static final String USE_TYPE_INFO_HEADERS =
        "spring.cloud.stream.kafka.streams.binder.configuration.spring.json"
            + ".use.type.headers";

    @Bean
    public StreamsBuilderFactoryBeanConfigurer streamsBuilderCustomizer() {

        return fb -> {
            fb.setKafkaStreamsCustomizer(
                kafkaStreams -> kafkaStreams.setUncaughtExceptionHandler(e -> {
                    LOG.error("Uncaught exception occurred.", e);
                    // default handler response
                    return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
                }));
        };
    }

    @Bean
    public Serde<Bundle> bundleSerde() {
        return new FhirSerde<>(Bundle.class);
    }

    @Bean
    public Serde<ORU_R01> hl7Serde() {
        return Serdes.serdeFrom(new Hl7Serializer<>(), new Hl7Deserializer<>());
    }

}
