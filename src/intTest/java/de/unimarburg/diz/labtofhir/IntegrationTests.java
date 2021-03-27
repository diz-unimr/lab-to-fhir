package de.unimarburg.diz.labtofhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = LabToFhirApplication.class)
public class IntegrationTests extends TestContainerBase {


    @DynamicPropertySource
    private static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrapServers", kafka::getBootstrapServers);
        registry.add("spring.cloud.stream.kafka.streams.binder.configuration.processing.guarantee",
            () -> "exactly_once");
        registry.add("services.pseudonymizer.url", () -> "http://" +
            pseudonymizerContainer.getHost() + ":" + pseudonymizerContainer.getFirstMappedPort()
            + "/fhir");
    }

    @BeforeAll
    public static void setupContainers() throws Exception {
        setup();
    }

    @Test
    void processorCreatesFhirBundles() {
        // create consumer
        var consumer = KafkaHelper.createConsumer(kafka.getBootstrapServers());
        // subscribe to target topic
        consumer.subscribe(Collections.singleton("test-fhir-laboratory"));

        var fetchCount = 10;

        var messages = KafkaHelper.drain(consumer, fetchCount);

        assertThat(messages.values()).hasOnlyElementsOfType(Bundle.class);
    }

    @Test
    void bundlesArePseudonymized() {
        // create consumer
        var consumer = KafkaHelper.createConsumer(kafka.getBootstrapServers());
        // subscribe to target topic
        consumer.subscribe(Collections.singleton("test-fhir-laboratory"));

        var fetchCount = 10;

        var messages = KafkaHelper.drain(consumer, fetchCount);
        var resources = messages.values().stream().map(Bundle.class::cast)
            .flatMap(x -> x.getEntry().stream().map(BundleEntryComponent::getResource))
            .collect(Collectors.toList());

        var pseudedCoding = new Coding(
            "http://terminology.hl7.org/CodeSystem/v3-ObservationValue",
            "PSEUDED",
            "part of the resource is pseudonymized");

        assertThat(resources)
            .extracting(r -> r.getMeta().getSecurityFirstRep())
            .allSatisfy(
                c -> assertThat(c).usingRecursiveComparison().isEqualTo(pseudedCoding));
    }


}
