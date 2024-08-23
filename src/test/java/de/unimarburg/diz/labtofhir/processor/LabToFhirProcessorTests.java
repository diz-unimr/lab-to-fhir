package de.unimarburg.diz.labtofhir.processor;

import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.configuration.MappingConfiguration;
import de.unimarburg.diz.labtofhir.mapper.AimLabMapper;
import de.unimarburg.diz.labtofhir.mapper.Hl7LabMapper;
import de.unimarburg.diz.labtofhir.mapper.LoincMapper;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;


@SpringBootTest(classes = {LabToFhirProcessor.class, AimLabMapper.class,
    Hl7LabMapper.class, LoincMapper.class, FhirConfiguration.class,
    MappingConfiguration.class})
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
        try (var driver = buildStream(processor.aim())) {

            var labTopic = createInputTopic(driver);
            var outputTopic = createOutputTopic(driver);

            var labReport = createReport(42, new Coding().setSystem(
                    fhirProperties.getSystems()
                        .getLaboratorySystem())
                .setCode("NA"));

            // create input record
            labTopic.pipeInput(String.valueOf(labReport.getId()), labReport);

            // get record from output topic
            var outputRecords = outputTopic.readRecordsToList();

            var obsCodes = getObservationsCodes(outputRecords).findAny()
                .orElseThrow();

            // assert both codings exist
            assertThat(
                obsCodes.hasCoding("http://loinc.org", "2951-2")).isTrue();
            assertThat(obsCodes.hasCoding(fhirProperties.getSystems()
                .getLaboratorySystem(), "NA")).isTrue();
        }
    }
}
