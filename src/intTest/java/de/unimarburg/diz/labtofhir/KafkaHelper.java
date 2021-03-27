package de.unimarburg.diz.labtofhir;

import static org.awaitility.Awaitility.await;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Durations;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.miracum.kafka.serializers.KafkaFhirDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;

public class KafkaHelper {

    public static Map<String, IBaseResource> drain(KafkaConsumer<String, IBaseResource> consumer,
        int fetchCount) {

        var allRecords = new HashMap<String, IBaseResource>();

        await().atMost(Durations.FIVE_MINUTES)
            .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
            .until(() -> {
                consumer.poll(java.time.Duration.ofMillis(50))
                    .iterator()
                    .forEachRemaining(r -> allRecords.put(r.key(), r.value()));

                return allRecords.size() >= fetchCount;
            });

        return allRecords;
    }

    public static KafkaConsumer<String, IBaseResource> createConsumer(String bootstrapServers) {
        var consumerProps = KafkaTestUtils.consumerProps(bootstrapServers,
            "tc-" + UUID.randomUUID(), "false");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            KafkaFhirDeserializer.class);
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, "test");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, IBaseResource.class);
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");

        return new KafkaConsumer<>(consumerProps);
    }
}
