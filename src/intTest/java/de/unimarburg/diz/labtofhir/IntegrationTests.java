package de.unimarburg.diz.labtofhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.assertj.core.api.Fail;
import org.awaitility.Durations;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.miracum.kafka.serializers.KafkaFhirDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(classes = LabToFhirApplication.class)
public class IntegrationTests {

    private final static Logger log = LoggerFactory.getLogger(IntegrationTests.class);
    private static Network network;
    private static KafkaContainer kafka;
    private static PostgreSQLContainer aimDb;
    private static GenericContainer kafkaConnect;

    @DynamicPropertySource
    private static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrapServers", kafka::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.streams.binder.configuration.processing.guarantee",
            () -> "exactly_once");
    }

    @BeforeAll
    public static void setupContainers() throws Exception {

        network = Network.newNetwork();

        // setup & start lufu-db
        aimDb = new PostgreSQLContainer<>(DockerImageName.parse("postgres:11-alpine"))
            .withDatabaseName("aim").withUsername("aim")
            .withPassword("test").withNetwork(network).withNetworkAliases("aim-db")
            .withClasspathResourceMapping("db",
                "/docker-entrypoint-initdb.d", BindMode.READ_ONLY);
        aimDb.start();

        // setup & start kafka
        kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:5.5.0")).withNetwork(network)
            .withEnv(Map.of("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1",
                "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1"));
        kafka.start();

        // setup & start kafka connect
        kafkaConnect = createKafkaConnectContainer();

        // create lufu-db connector
        initConnect();
    }

    private static GenericContainer createKafkaConnectContainer() {
        Integer restPort = 8083;
        var connectProps = new HashMap<String, String>();
        connectProps.put("CONNECT_BOOTSTRAP_SERVERS", kafka.getNetworkAliases()
            .get(0) + ":9092");
        connectProps.put("CONNECT_REST_PORT", String.valueOf(restPort));
        connectProps.put("CONNECT_GROUP_ID", "connect-group");
        connectProps.put("CONNECT_CONFIG_STORAGE_TOPIC", "docker-connect-configs");
        connectProps.put("CONNECT_OFFSET_STORAGE_TOPIC", "docker-connect-offsets");
        connectProps.put("CONNECT_STATUS_STORAGE_TOPIC", "docker-connect-status");
        connectProps.put("CONNECT_KEY_CONVERTER", "org.apache.kafka.connect.json.JsonConverter");
        connectProps.put("CONNECT_VALUE_CONVERTER", "org.apache.kafka.connect.json.JsonConverter");
        connectProps.put("CONNECT_INTERNAL_KEY_CONVERTER",
            "org.apache.kafka.connect.json.JsonConverter");
        connectProps.put("CONNECT_INTERNAL_VALUE_CONVERTER",
            "org.apache.kafka.connect.json.JsonConverter");
        connectProps.put("CONNECT_REST_ADVERTISED_HOST_NAME", "kafka-connect");
        connectProps.put("CONNECT_CONFIG_STORAGE_REPLICATION_FACTOR", "1");
        connectProps.put("CONNECT_OFFSET_STORAGE_REPLICATION_FACTOR", "1");
        connectProps.put("CONNECT_STATUS_STORAGE_REPLICATION_FACTOR", "1");
        connectProps.put("CONNECT_PLUGIN_PATH", "/usr/share/java");

        return new GenericContainer<>(
            DockerImageName.parse("confluentinc/cp-kafka-connect:5.5.0")).withEnv(connectProps)
            .waitingFor(Wait.forHttp("/connectors"))
            .withNetwork(network)
            .withExposedPorts(restPort);
    }

    private static void initConnect() throws Exception {
        kafkaConnect.start();

        addConnectConfiguration("connect/connect-lab.json");
    }

    private static void addConnectConfiguration(String configFilename) throws Exception {
        // post connect configuration to REST endpoint
        var host = "http://" + kafkaConnect.getHost() + ":" + kafkaConnect.getFirstMappedPort()
            + "/connectors";
        var request = HttpRequest.newBuilder()
            .uri(new URI(host))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofFile(Path.of(ClassLoader.getSystemResource(configFilename)
                .toURI())))
            .build();

        var response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            Fail.fail("Error setting up aim-db connector: " + response);
        }
    }

    @Test
    void processorCreatesFhirBundles() {
        // create consumer
        var consumer = createConsumer();
        // subscribe to target topic
        consumer.subscribe(Collections.singleton("test-fhir-laboratory"));

        var fetchCount = 10;

        var messages = drain(consumer, fetchCount);

        assertThat(messages.values()).hasOnlyElementsOfType(Bundle.class);
    }

    private KafkaConsumer<String, IBaseResource> createConsumer() {
        var consumerProps = KafkaTestUtils.consumerProps(kafka.getBootstrapServers(),
            "tc-" + UUID.randomUUID(), "false");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            KafkaFhirDeserializer.class);
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, "test");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, IBaseResource.class);
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, "false");

        return new KafkaConsumer<>(consumerProps);
    }

    private Map<String, IBaseResource> drain(KafkaConsumer<String, IBaseResource> consumer,
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
}
