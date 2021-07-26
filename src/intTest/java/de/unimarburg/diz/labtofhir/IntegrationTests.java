package de.unimarburg.diz.labtofhir;

import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.mapper.LoincMapper;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = {LabToFhirApplication.class, LoincMapper.class})
@TestPropertySource(properties = {
    "mapping.loinc.version=''", "mapping.loinc.credentials.user=''",
    "mapping.loinc.credentials.password=''",
    "mapping.loinc.local=mapping-swl-loinc.zip"})
public class IntegrationTests extends TestContainerBase {

    @DynamicPropertySource
    private static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrapServers", kafka::getBootstrapServers);
        registry.add("services.pseudonymizer.url", () -> "http://" +
            pseudonymizerContainer.getHost() + ":" + pseudonymizerContainer.getFirstMappedPort()
            + "/fhir");
    }

    @BeforeAll
    public static void setupContainers() throws Exception {
        setup();
    }

    @Test
    public void bundlesArePseudonymized() {
        var messages = KafkaHelper
            .getAtLeast(
                KafkaHelper.createFhirTopicConsumer(kafka.getBootstrapServers()),
                "test-fhir-laboratory",
                10);
        var resources = messages.stream().map(Bundle.class::cast)
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

    @Test
    public void messagesAreSentToUnmappedTopic() {
        var messages = KafkaHelper
            .getAtLeast(KafkaHelper.createErrorTopicConsumer(kafka.getBootstrapServers()),
                "test-fhir-laboratory-error", 1);

        assertThat(messages).isNotEmpty();
    }


}
