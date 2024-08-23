package de.unimarburg.diz.labtofhir;

import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.processor.LabToFhirProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.endpoint.BindingsEndpoint;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;


@EmbeddedKafka(partitions = 1, brokerProperties = {
    "listeners=PLAINTEXT://localhost:9092", "port=9092"})
@SpringBootTest
@Import(BindingsEndpoint.class)
@TestPropertySource(properties = {"mapping.loinc.version=''",
    "mapping.loinc.credentials.user=''",
    "mapping.loinc.credentials.password=''",
    "mapping.loinc.local=mapping-swl-loinc.zip",
    "spring.cloud.stream.kafka.streams.binder.replicationFactor=1",
    "spring.cloud.stream.kafka.streams.binder.minPartitionCount=1"})
public class LabToFhirConfigurationTests {

    @Autowired
    private LabToFhirProcessor labProcessor;

    @Test
    void contexLoads() {
        assertThat(labProcessor).isNotNull();
    }

}
