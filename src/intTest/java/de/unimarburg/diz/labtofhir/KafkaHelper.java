package de.unimarburg.diz.labtofhir;

import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
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
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Durations;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.miracum.kafka.serializers.KafkaFhirDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;

public class KafkaHelper {

    public static <K, V> List<V> getAtLeast(Consumer<K, V> consumer, String topic,
        int fetchCount) {
        consumer.subscribe(Collections.singleton(topic));
        var records = KafkaTestUtils
            .getRecords(consumer, Durations.FIVE_MINUTES.toMillis(),
                fetchCount);
        return StreamSupport
            .stream(records.spliterator(), false).map(ConsumerRecord::value)
            .collect(Collectors.toList());
    }

    public static <K, V> KafkaConsumer<K, V> createConsumer(
        String bootstrapServers, Class<? extends Deserializer<K>> keyDeserializer,
        Class<? extends Deserializer<V>> valueDeserializer) {

        var consumerProps = KafkaTestUtils.consumerProps(bootstrapServers,
            "tc-" + UUID.randomUUID(), "false");
        consumerProps
            .put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            valueDeserializer);
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, "test");
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");

        return new KafkaConsumer<>(consumerProps);
    }

    public static KafkaConsumer<String, IBaseResource> createFhirTopicConsumer(
        String bootstrapServers) {
        return KafkaHelper.createConsumer(bootstrapServers,
            StringDeserializer.class,
            KafkaFhirDeserializer.class);
    }

    public static KafkaConsumer<String, LaboratoryReport> createErrorTopicConsumer(
        String bootstrapServers) {
        return KafkaHelper.createConsumer(bootstrapServers,
            StringDeserializer.class,
            LaboratoryReportDeserializer.class);
    }
}
