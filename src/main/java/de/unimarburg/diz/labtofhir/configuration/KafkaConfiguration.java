package de.unimarburg.diz.labtofhir.configuration;

import de.unimarburg.diz.labtofhir.serializer.FhirSerde;
import java.util.Objects;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;

@Configuration
@EnableKafka
public class KafkaConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(
        KafkaConfiguration.class);

    @SuppressWarnings("checkstyle:LineLength")
    @Bean
    public StreamsBuilderFactoryBeanConfigurer streamsBuilderCustomizer(
        @Value("${app.kafka.rocksdb.level-compaction:false}") boolean enableLevelCompaction) {
        return factoryBean -> {
            factoryBean.setKafkaStreamsCustomizer(
                kafkaStreams -> kafkaStreams.setUncaughtExceptionHandler(e -> {
                    LOG.error("Uncaught exception occurred.", e);
                    // default handler response
                    return StreamsUncaughtExceptionHandler.StreamThreadExceptionResponse.SHUTDOWN_CLIENT;
                }));

            if (enableLevelCompaction) {
                Objects
                    .requireNonNull(factoryBean.getStreamsConfiguration())
                    .put(StreamsConfig.ROCKSDB_CONFIG_SETTER_CLASS_CONFIG,
                        RocksDbConfig.class);
            }
        };
    }

    @Bean
    public Serde<Bundle> bundleSerde() {
        return new FhirSerde<>(Bundle.class);
    }
}
