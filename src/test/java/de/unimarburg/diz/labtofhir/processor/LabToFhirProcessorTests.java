package de.unimarburg.diz.labtofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.configuration.MappingConfiguration;
import de.unimarburg.diz.labtofhir.mapper.LoincMapper;
import de.unimarburg.diz.labtofhir.mapper.MiiLabReportMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.serde.JsonSerdes;
import de.unimarburg.diz.labtofhir.serializer.FhirDeserializer;
import de.unimarburg.diz.labtofhir.serializer.FhirSerializer;
import java.util.List;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.TestPropertySource;


@SpringBootTest(classes = {LabToFhirProcessor.class, MiiLabReportMapper.class, LoincMapper.class,
    FhirConfiguration.class, MappingConfiguration.class})
@TestPropertySource(properties = {"mapping.loinc.version=''", "mapping.loinc.credentials.user=''",
    "mapping.loinc.credentials.password=''", "mapping.loinc.local=mapping-swl-loinc.zip"})

public class LabToFhirProcessorTests {

    @Autowired
    private LabToFhirProcessor processor;


    @Autowired
    private FhirProperties fhirProperties;

    @Test
    public void observationIsLoincMapped() {

        // build stream
        var builder = new StreamsBuilder();
        final KStream<String, LaboratoryReport> labStream = builder.stream("lab",
            Consumed.with(Serdes.String(), JsonSerdes.LaboratoryReport()));
        //        final KTable<String, LoincMap> loincTable = builder.table("loinc",
        //            Consumed.with(Serdes.String(), JsonSerdes.LoincMapEntry()));
        processor
            .process()
            .apply(labStream)
            .to("lab-mapped", Produced.with(Serdes.String(),
                Serdes.serdeFrom(new FhirSerializer<>(), new FhirDeserializer<>(Bundle.class))));

        try (var driver = new TopologyTestDriver(builder.build())) {

            var labTopic = driver.createInputTopic("lab", new IntegerSerializer(),
                new JsonSerializer<>());
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

            var obs = new Observation()
                .addIdentifier(new Identifier().setValue("obs-id"))
                .setCode(new CodeableConcept().setCoding(List.of(new Coding(fhirProperties
                    .getSystems()
                    .getLaboratorySystem(), "NA", null))))
                .setValue(new Quantity(1));
            obs.setId("obs-id");
            labReport.setObservations(List.of(obs));

            // create input record
            labTopic.pipeInput(labReport.getId(), labReport);

            // get record from output topic
            var outputRecords = outputTopic.readRecordsToList();

            var obsCodes = outputRecords
                .stream()
                .flatMap(b -> b
                    .getValue()
                    .getEntry()
                    .stream()
                    .map(BundleEntryComponent::getResource))
                .filter(Observation.class::isInstance)
                .map(Observation.class::cast)
                .map(Observation::getCode)
                //                .max(Comparator.comparing(TestRecord::getRecordTime))
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
