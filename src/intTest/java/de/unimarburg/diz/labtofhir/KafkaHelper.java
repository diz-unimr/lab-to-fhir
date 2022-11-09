package de.unimarburg.diz.labtofhir;

import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.serializer.FhirDeserializer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Durations;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;

public class KafkaHelper {

    public static <K, V> List<V> getAtLeast(Consumer<K, V> consumer, String topic, int fetchCount) {
        consumer.subscribe(Collections.singleton(topic));
        var records = KafkaTestUtils.getRecords(consumer, Durations.TWO_MINUTES.toMillis(),
            fetchCount);
        return StreamSupport
            .stream(records.spliterator(), false)
            .map(ConsumerRecord::value)
            .collect(Collectors.toList());
    }

    public static void addTopics(String... topicNames) {
        new KafkaAdmin(Map.of()).createOrModifyTopics(Arrays
            .stream(topicNames)
            .map(x -> new NewTopic(x, Optional.empty(), Optional.empty()))
            .toList()
            .toArray(new NewTopic[]{}));
    }

    public static <K, V> KafkaConsumer<K, V> createConsumer(String bootstrapServers,
        Deserializer<K> keyDeserializer, Deserializer<V> valueDeserializer) {

        var consumerProps = KafkaTestUtils.consumerProps(bootstrapServers,
            "tc-" + UUID.randomUUID(), "false");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, "test");
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");

        return new KafkaConsumer<>(consumerProps, keyDeserializer, valueDeserializer);
    }

    public static <K, V> KafkaProducer<K, V> createProducer(String bootstrapServers,
        Serializer<K> keySerializer, Serializer<V> valueSerializer) {

        var producerProps = KafkaTestUtils.producerProps(bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, "test-producer");

        return new KafkaProducer<K, V>(producerProps, keySerializer, valueSerializer);
    }

    public static KafkaConsumer<String, Bundle> createFhirTopicConsumer(String bootstrapServers) {
        return KafkaHelper.createConsumer(bootstrapServers, new StringDeserializer(),
            new FhirDeserializer<>(Bundle.class));
    }

    public static KafkaConsumer<Integer, LaboratoryReport> createErrorTopicConsumer(
        String bootstrapServers) {
        return KafkaHelper.createConsumer(bootstrapServers, new IntegerDeserializer(),
            new JsonDeserializer<>(LaboratoryReport.class));
    }
}
