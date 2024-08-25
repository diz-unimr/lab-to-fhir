package de.unimarburg.diz.labtofhir.processor;

import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.mapper.AimLabMapper;
import de.unimarburg.diz.labtofhir.mapper.Hl7LabMapper;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(classes = {LabToFhirProcessor.class, AimLabMapper.class,
    Hl7LabMapper.class, FhirConfiguration.class})

public class LabToFhirProcessorTests extends BaseProcessorTests {

    @Autowired
    private LabToFhirProcessor processor;


    @Autowired
    private FhirProperties fhirProperties;

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    public void observationCodeIsMapped() {
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
            assertThat(obsCodes.hasCoding(fhirProperties.getSystems()
                .getLaboratorySystem(), "NA")).isTrue();
        }
    }
}
