package de.unimarburg.diz.labtofhir.stream;


import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.LoincMap;
import de.unimarburg.diz.labtofhir.model.LoincMapEntry;
import de.unimarburg.diz.labtofhir.serde.Serializers.LaboratoryReportSerializer;
import de.unimarburg.diz.labtofhir.serde.Serializers.LoincMapSerializer;
import de.unimarburg.diz.labtofhir.serializer.FhirDeserializer;
import java.util.ArrayList;
import java.util.Arrays;
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

public class LabLoincStreamTests {

    //    @Disabled("TODO remove")
    @Test
    public void observationIsLoincMapped() {

        // TODO inject properties
        var fhirProperties = new FhirProperties();
        fhirProperties.setGenerateNarrative(false);
        fhirProperties
            .getSystems()
            .setLaboratorySystem("https://fhir.diz.uni-marburg.de/CodeSystem/swisslab-code");
        fhirProperties
            .getSystems()
            .setLabReportMetaSystem(
                "https://fhir.diz.uni-marbrug.de/CodeSystem/lab-report-meta-code");

        var builder = LabLoincStream.createStream(new StreamsBuilder(), fhirProperties);

        try (var driver = new TopologyTestDriver(builder.build())) {

            var labTopic = driver.createInputTopic("lab", new IntegerSerializer(),
                new LaboratoryReportSerializer());
            var loincTopic = driver.createInputTopic("loinc", new StringSerializer(),
                new LoincMapSerializer());
            var outputTopic = driver.createOutputTopic("lab-mapped", new StringDeserializer(),
                new FhirDeserializer<>(Bundle.class));

            var labReport = new LaboratoryReport();
            labReport.setId(42);
            labReport.setResource(new DiagnosticReport()
                .addIdentifier(new Identifier().setValue("report-id"))
                .setSubject(
                    new Reference(new Patient().addIdentifier(new Identifier().setValue("1"))))
                .setEncounter(
                    new Reference(new Encounter().addIdentifier(new Identifier().setValue("1")))));
            var obsList = new ArrayList<Observation>();
            obsList.add(new Observation()
                .addIdentifier(new Identifier().setValue("obs-id"))
                .setCode(new CodeableConcept().setCoding(Arrays.asList(new Coding().setCode("NA"))))
                .setValue(new Quantity(1)));
            labReport.setObservations(obsList);
            var loincMap = new LoincMap()
                .setSwl("NA")
                .setEntries(List.of(new LoincMapEntry()
                    .setLoinc("2951-2")
                    .setUcum("mmol/L")));

            labTopic.pipeInput(labReport.getId(), labReport);
            loincTopic.pipeInput(loincMap.getSwl(), loincMap);

            var bla = driver.producedTopicNames();

            var outputRecords = outputTopic.readKeyValuesToList();

            var mappedBundle = outputRecords
                .stream()
                .filter(x -> x.value
                    .getEntryFirstRep()
                    .getResource()
                    .getResourceType() == ResourceType.Observation)
                .findAny()
                .orElseThrow().value;

            var obsCodes = mappedBundle
                .getEntry()
                .stream()
                .map(BundleEntryComponent::getResource)
                .filter(Observation.class::isInstance)
                .map(Observation.class::cast)
                .map(Observation::getCode)
                .findAny()
                .orElseThrow();

            // assert both codings exist
            assertThat(obsCodes.hasCoding("http://loinc.org", "2951-2")).isTrue();
            assertThat(obsCodes.hasCoding(fhirProperties
                .getSystems()
                .getLaboratorySystem(), "NA")).isTrue();
        }
    }
}
