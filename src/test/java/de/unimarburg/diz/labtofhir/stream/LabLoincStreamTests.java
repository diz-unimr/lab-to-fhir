package de.unimarburg.diz.labtofhir.stream;


import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.LoincMapEntry;
import de.unimarburg.diz.labtofhir.serde.Serializers.LaboratoryReportSerializer;
import de.unimarburg.diz.labtofhir.serde.Serializers.LoincMapEntrySerializer;
import de.unimarburg.diz.labtofhir.serializer.FhirDeserializer;
import java.io.IOException;
import java.util.List;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TopologyTestDriver;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

public class LabLoincStreamTests {

    @Test
    public void observationIsLoincMapped() {
        var builder = LabLoincStream.createStream(new StreamsBuilder());

        try (var driver = new TopologyTestDriver(builder.build())) {

            var labTopic = driver.createInputTopic("lab", new IntegerSerializer(),
                new LaboratoryReportSerializer());
            var loincTopic = driver.createInputTopic("loinc", new StringSerializer(),
                new LoincMapEntrySerializer());
            var outputTopic = driver.createOutputTopic("lab-mapped", new StringDeserializer(),
                new FhirDeserializer<>(Bundle.class));

            //            var labReport = getTestReport();
            var labReport = new LaboratoryReport();
            labReport.setId(42);
            labReport.setResource(new DiagnosticReport()
                .setSubject(
                    new Reference(new Patient().addIdentifier(new Identifier().setValue("1"))))
                .setEncounter(
                    new Reference(new Encounter().addIdentifier(new Identifier().setValue("1")))));
            labReport.setObservations(List.of(new Observation()
                .setCode(new CodeableConcept().setCoding(List.of(new Coding().setCode("NA"))))
                .setValue(new Quantity(1))));
            var loincMapEntry = new LoincMapEntry();
            loincMapEntry.setSwl("NA");
            loincMapEntry.setLoinc("2951-2");
            loincMapEntry.setUcum("mmol/L");

            loincTopic.pipeInput(loincMapEntry.getSwl(), loincMapEntry);
            labTopic.pipeInput(labReport.getId(), labReport);

            var produced = driver.producedTopicNames();

            var outputRecords = outputTopic.readKeyValuesToList();

            var mappedBundle = outputRecords
                .stream()
                .filter(x -> x.value
                    .getEntryFirstRep()
                    .getResource()
                    .getResourceType() == ResourceType.Observation)
                .findAny()
                .orElseThrow().value;
            var obsCoding = mappedBundle
                .getEntry()
                .stream()
                .map(BundleEntryComponent::getResource)
                .filter(Observation.class::isInstance)
                .map(Observation.class::cast)
                .flatMap(c -> c
                    .getCode()
                    .getCoding()
                    .stream())
                .toList();

            // assert
            //            assertThat(outputTopic.isEmpty()).isFalse();
            assertThat(obsCoding
                .stream()
                .filter(x -> x
                    .getCode()
                    .equals(loincMapEntry.getLoinc()))
                .findAny()).isNotEmpty();
        }
    }

    private LaboratoryReport getTestReport() {

        var resource = new ClassPathResource("reports/test-input.json");

        LaboratoryReport report;
        try {
            var objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            report = objectMapper.readValue(resource.getInputStream(), LaboratoryReport.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return report;
    }


}
