package de.unimarburg.diz.labtofhir;

import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Fail.fail;

public abstract class TestContainerBase {

    protected static KafkaContainer kafka;
    protected static GenericContainer pseudonymizerContainer;

    protected static void setup() throws Exception {

        var network = Network.newNetwork();

        // setup & start aim db
        var aimDb = createAimDbContainer(network);
        aimDb.start();

        // setup & start kafka
        kafka = createKafkaContainer(network);
        kafka.start();

        // setup & start kafka connect
        createKafkaConnectContainer(kafka);
    }

    private static PostgreSQLContainer createAimDbContainer(Network network) {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:11-alpine"))
            .withDatabaseName("aim").withUsername("aim")
            .withPassword("test").withNetwork(network).withNetworkAliases("aim-db")
            .withClasspathResourceMapping("db",
                "/docker-entrypoint-initdb.d", BindMode.READ_ONLY);
    }

    public static KafkaContainer createKafkaContainer(Network network) {
        return new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:5.5.0")).withNetwork(network)
            .withEnv(Map.of("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1",
                "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1"));
    }

    private static GenericContainer createKafkaConnectContainer(KafkaContainer kafka)
        throws Exception {
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

        var connect = new GenericContainer<>(
            DockerImageName.parse("confluentinc/cp-kafka-connect:5.5.0")).withEnv(connectProps)
            .waitingFor(Wait.forHttp("/connectors"))
            .withNetwork(kafka.getNetwork())
            .withExposedPorts(restPort);

        // create lab-db connector
        connect.start();
        addConnector(connect, "connect/connect-lab.json");

        return connect;
    }

    private static void addConnector(GenericContainer connect, String configFilename)
        throws Exception {
        // post connect configuration to REST endpoint
        var host = "http://" + connect.getHost() + ":" + connect.getFirstMappedPort()
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
            fail("Error setting up aim-db connector: " + response);
        }
    }
}


