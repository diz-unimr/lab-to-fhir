package de.unimarburg.diz.labtofhir;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Durations;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.miracum.kafka.serializers.KafkaFhirDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;

public class KafkaHelper {

    public static Map<String, IBaseResource> getAtLeast(String bootstrapServers, String topic,
        int fetchCount) {
        var consumer = createConsumer(bootstrapServers);
        consumer.subscribe(Collections.singleton(topic));
        var records = KafkaTestUtils
            .getRecords(consumer, Durations.ONE_MINUTE.toMillis(),
                fetchCount);
        return StreamSupport
            .stream(records.spliterator(), false)
            .collect(Collectors.toMap(ConsumerRecord::key, ConsumerRecord::value));
    }

    public static KafkaConsumer<String, IBaseResource> createConsumer(String bootstrapServers) {
        var consumerProps = KafkaTestUtils.consumerProps(bootstrapServers,
            "tc-" + UUID.randomUUID(), "false");
        consumerProps
            .put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            KafkaFhirDeserializer.class);
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, "test");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, IBaseResource.class);
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");

        return new KafkaConsumer<>(consumerProps);
    }
}
