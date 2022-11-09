package de.unimarburg.diz.labtofhir;

import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

public abstract class TestContainerBase {

    protected static KafkaContainer kafka;
    protected static GenericContainer pseudonymizerContainer;

    protected static void setup() {

        var network = Network.newNetwork();

        // setup & start kafka
        kafka = createKafkaContainer(network);
        kafka.start();
    }

    public static KafkaContainer createKafkaContainer(Network network) {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.5.0"))
            .withNetwork(network)
            .withEnv(Map.of("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1",
                "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1"));
    }
}


