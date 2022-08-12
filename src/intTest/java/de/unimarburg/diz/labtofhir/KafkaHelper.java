package de.unimarburg.diz.labtofhir;

import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.serializer.FhirDeserializer;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Durations;
import org.hl7.fhir.r4.model.Bundle;
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
