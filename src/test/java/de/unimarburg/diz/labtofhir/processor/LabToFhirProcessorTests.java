package de.unimarburg.diz.labtofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.configuration.MappingConfiguration;
import de.unimarburg.diz.labtofhir.mapper.LoincMapper;
import de.unimarburg.diz.labtofhir.mapper.MiiLabReportMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.serde.JsonSerdes;
import java.util.List;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
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
import org.springframework.test.context.TestPropertySource;


@SpringBootTest(classes = {LabToFhirProcessor.class, MiiLabReportMapper.class,
    LoincMapper.class, FhirConfiguration.class, MappingConfiguration.class})
@TestPropertySource(properties = {"mapping.loinc.version=''",
    "mapping.loinc.credentials.user=''",
    "mapping.loinc.credentials.password=''",
    "mapping.loinc.local=mapping-swl-loinc.zip"})

public class LabToFhirProcessorTests extends BaseProcessorTests {

    @Autowired
    private LabToFhirProcessor processor;


    @Autowired
    private FhirProperties fhirProperties;

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    public void observationIsLoincMapped() {

        // build stream
        var builder = new StreamsBuilder();
        final KStream<String, LaboratoryReport> labStream = builder.stream(
            "lab",
            Consumed.with(Serdes.String(), JsonSerdes.laboratoryReport()));

        // build stream
        try (var driver = buildStream(processor.process())) {

            var labTopic = createInputTopic(driver);
            var outputTopic = createOutputTopic(driver);

            var labReport = new LaboratoryReport();
            labReport.setId(42);
            labReport.setResource(new DiagnosticReport()
                .addIdentifier(new Identifier().setValue("report-id"))
                .setSubject(new Reference(new Patient().addIdentifier(
                    new Identifier().setValue("1"))))
                .setEncounter(new Reference(new Encounter().addIdentifier(
                    new Identifier().setValue("1")))));

            var obs = new Observation()
                .addIdentifier(new Identifier().setValue("obs-id"))
                .setCode(new CodeableConcept().setCoding(List.of(new Coding(
                    fhirProperties
                        .getSystems()
                        .getLaboratorySystem(), "NA", null))))
                .setValue(new Quantity(1));
            obs.setId("obs-id");
            labReport.setObservations(List.of(obs));

            // create input record
            labTopic.pipeInput(String.valueOf(labReport.getId()), labReport);

            // get record from output topic
            var outputRecords = outputTopic.readRecordsToList();

            var obsCodes = getObservationsCodes(outputRecords)
                .findAny()
                .orElseThrow();

            // assert both codings exist
            assertThat(
                obsCodes.hasCoding("http://loinc.org", "2951-2")).isTrue();
            assertThat(obsCodes.hasCoding(fhirProperties
                .getSystems()
                .getLaboratorySystem(), "NA")).isTrue();
        }
    }
}
