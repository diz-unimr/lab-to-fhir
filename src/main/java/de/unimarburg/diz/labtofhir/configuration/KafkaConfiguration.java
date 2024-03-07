package de.unimarburg.diz.labtofhir.configuration;

import de.unimarburg.diz.labtofhir.model.LabOffsets;
import de.unimarburg.diz.labtofhir.model.MappingUpdate;
import de.unimarburg.diz.labtofhir.serializer.FhirSerde;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.errors.StreamsUncaughtExceptionHandler;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.retry.annotation.EnableRetry;

@SuppressWarnings("checkstyle:LineLength")
@Configuration
@EnableKafka
@EnableRetry
public class KafkaConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(
        KafkaConfiguration.class);
    private static final String USE_TYPE_INFO_HEADERS = "spring.cloud.stream.kafka.streams.binder.configuration.spring.json.use.type.headers";

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
    public LabOffsets getOffsets(KafkaAdmin kafkaAdmin,
        @Value("${spring.cloud.stream.kafka.streams.binder.functions.process.applicationId}") String processGroup,
        @Value("${spring.cloud.stream.kafka.streams.binder.functions.update.applicationId}") String updateGroup)
        throws ExecutionException, InterruptedException {
        // get current offsets
        try (var client = AdminClient.create(
            kafkaAdmin.getConfigurationProperties())) {
            var processOffsets = client
                .listConsumerGroupOffsets(processGroup)
                .partitionsToOffsetAndMetadata()
                .get();

            var updateOffsets = client
                .listConsumerGroupOffsets(updateGroup)
                .partitionsToOffsetAndMetadata()
                .get();

            return new LabOffsets(processOffsets
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e
                    .getKey()
                    .partition(), Map.Entry::getValue)), updateOffsets
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e -> e
                    .getKey()
                    .partition(), Map.Entry::getValue)));
        }
    }

    @Bean
    public Producer<String, MappingUpdate> createUpdateProducer(
        DefaultKafkaProducerFactory<String, MappingUpdate> pf) {

        var props = new HashMap<>(pf.getConfigurationProperties());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
            StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
            JsonSerializer.class);

        return new KafkaProducer<>(props);
    }

    @Bean
    public NewTopic mappingTopic(
        @Value("${spring.cloud.stream.kafka.streams.binder.replicationFactor}") int replicas) {
        return TopicBuilder
            .name("mapping")
            .partitions(1)
            .replicas(replicas)
            .build();
    }

    @Bean
    public Consumer<String, MappingUpdate> createUpdateConsumer(
        DefaultKafkaConsumerFactory<String, MappingUpdate> cf,
        @Value("${" + USE_TYPE_INFO_HEADERS + "}") boolean useTypeHeaders) {

        var props = new HashMap<>(cf.getConfigurationProperties());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "mapping-update");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, MappingUpdate.class);
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, useTypeHeaders);

        return new KafkaConsumer<>(props);
    }

    @Bean
    public Serde<Bundle> bundleSerde() {
        return new FhirSerde<>(Bundle.class);
    }

}
