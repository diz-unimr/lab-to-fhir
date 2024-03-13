package de.unimarburg.diz.labtofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.configuration.MappingConfiguration;
import de.unimarburg.diz.labtofhir.mapper.LoincMapper;
import de.unimarburg.diz.labtofhir.mapper.MiiLabReportMapper;
import de.unimarburg.diz.labtofhir.model.LabOffsets;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.MappingInfo;
import de.unimarburg.diz.labtofhir.model.MappingUpdate;
import de.unimarburg.diz.labtofhir.processor.LabUpdateProcessorTests.KafkaConfig;
import de.unimarburg.diz.labtofhir.serde.JsonSerdes;
import de.unimarburg.diz.labtofhir.serializer.FhirDeserializer;
import de.unimarburg.diz.labtofhir.serializer.FhirSerializer;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {LabUpdateProcessor.class, MiiLabReportMapper.class,
    LoincMapper.class, FhirConfiguration.class, MappingConfiguration.class,
    KafkaConfig.class})
@TestPropertySource(properties = {"mapping.loinc.version=''",
    "mapping.loinc.credentials.user=''",
    "mapping.loinc.credentials.password=''",
    "mapping.loinc.local=mapping-swl-loinc.zip"})
public class LabUpdateProcessorTests {

    @Autowired
    private LabUpdateProcessor processor;


    @Autowired
    private FhirProperties fhirProperties;

    @SuppressWarnings({"checkstyle:MagicNumber", "checkstyle:LineLength"})
    @Test
    void updateIsProcessed() {

        // build stream
        var builder = new StreamsBuilder();
        final KStream<String, LaboratoryReport> labStream = builder.stream(
            "lab",
            Consumed.with(Serdes.String(), JsonSerdes.laboratoryReport()));

        processor
            .update()
            .apply(labStream)
            .to("output-topic", Produced.with(Serdes.String(),
                Serdes.serdeFrom(new FhirSerializer<>(),
                    new FhirDeserializer<>(Bundle.class))));

        try (var driver = new TopologyTestDriver(builder.build())) {

            TestInputTopic<String, LaboratoryReport> labTopic = driver.createInputTopic(
                "lab", new StringSerializer(), new JsonSerializer<>());
            var outputTopic = driver.createOutputTopic("output-topic",
                new StringDeserializer(), new FhirDeserializer<>(Bundle.class));

            var inputReports = List.of(createReport(1, "NA"),
                createReport(2, "ERY"), createReport(3, "NA"),
                createReport(4, "NA"));

            // create input records
            labTopic.pipeKeyValueList(inputReports
                .stream()
                .map(r -> new KeyValue<>(String.valueOf(r.getId()), r))
                .toList());

            // get record from output topic
            var outputRecords = outputTopic.readRecordsToList();

            // expected keys are: 1, 3
            assertThat(outputRecords
                .stream()
                .map(TestRecord::getKey)
                .toList()).isEqualTo(List.of("1", "3"));

            // assert codes are mapped
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
                .toList();

            assertThat(obsCodes).allMatch(
                c -> c.hasCoding("http://loinc.org", "2951-2"));
        }
    }

    private LaboratoryReport createReport(int reportId, String labCode) {
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
            .setCode(new CodeableConcept().setCoding(List.of(new Coding(
                fhirProperties
                    .getSystems()
                    .getLaboratorySystem(), labCode, null))))
            .setValue(new Quantity(1));
        obs.setId("obs-id");
        report.setObservations(List.of(obs));

        return report;
    }

    @TestConfiguration
    static class KafkaConfig {

        @Bean
        LabOffsets testOffsets() {
            // offset target will be 2 on partition 0
            return new LabOffsets(Map.of(0, new OffsetAndMetadata(2L)),
                Map.of());
        }

        @Bean
        MappingInfo testMappingInfo() {
            return new MappingInfo(new MappingUpdate(null, null, List.of("NA")),
                false);
        }
    }

}