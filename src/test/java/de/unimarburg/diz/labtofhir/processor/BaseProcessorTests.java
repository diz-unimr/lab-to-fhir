package de.unimarburg.diz.labtofhir.processor;

import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.serializer.FhirDeserializer;
import de.unimarburg.diz.labtofhir.serializer.FhirSerializer;
import de.unimarburg.diz.labtofhir.serializer.Hl7Serializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.test.TestRecord;
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
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

@SuppressWarnings("checkstyle:LineLength")
abstract class BaseProcessorTests {

    Stream<CodeableConcept> getObservationsCodes(
        List<TestRecord<String, Bundle>> records) {
        return records
            .stream()
            .flatMap(b -> b
                .getValue()
                .getEntry()
                .stream()
                .map(BundleEntryComponent::getResource))
            .filter(Observation.class::isInstance)
            .map(Observation.class::cast)
            .map(Observation::getCode);
    }

    TestInputTopic<String, LaboratoryReport> createAimInputTopic(
        TopologyTestDriver driver) {
        return driver.createInputTopic("lab", new StringSerializer(),
            new JsonSerializer<>());
    }

    TestInputTopic<String, ORU_R01> createHl7InputTopic(
        TopologyTestDriver driver) {
        return driver.createInputTopic("lab", new StringSerializer(),
            new Hl7Serializer<>());
    }

    TestOutputTopic<String, Bundle> createOutputTopic(
        TopologyTestDriver driver) {
        return driver.createOutputTopic("lab-mapped", new StringDeserializer(),
            new FhirDeserializer<>(Bundle.class));
    }

    <T> TopologyTestDriver buildStream(
        Function<KStream<String, T>, KStream<String, Bundle>> processor,
        Serde<T> serde) {
        var builder = new StreamsBuilder();
        final KStream<String, T> labStream = builder.stream(
            "lab",
            Consumed.with(Serdes.String(), serde));

        processor
            .apply(labStream)
            .to("lab-mapped", Produced.with(Serdes.String(),
                Serdes.serdeFrom(new FhirSerializer<>(),
                    new FhirDeserializer<>(Bundle.class))));

        return new TopologyTestDriver(builder.build());
    }

    LaboratoryReport createReport(int reportId, Coding labCoding) {
        var report = new LaboratoryReport();
        report.setId(reportId);
        report.setResource(new DiagnosticReport()
            .addIdentifier(new Identifier().setValue("report-id"))
            .setSubject(new Reference(
                new Patient().addIdentifier(new Identifier().setValue("1"))))
            .setEncounter(new Reference(new Encounter().addIdentifier(
                new Identifier().setValue("1")))));

        var obs = new Observation()
            .addIdentifier(new Identifier().setValue("obs-id"))
            .setCode(new CodeableConcept().setCoding(List.of(labCoding)))
            .setValue(new Quantity(1));
        obs.setId("obs-id");
        report.setObservations(List.of(obs));

        return report;
    }
}
