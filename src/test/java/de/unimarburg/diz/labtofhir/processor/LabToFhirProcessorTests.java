package de.unimarburg.diz.labtofhir.processor;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.mapper.AimLabMapper;
import de.unimarburg.diz.labtofhir.mapper.Hl7LabMapper;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

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
        try (var driver = buildAimStream(processor.aim())) {

            var labTopic = createAimInputTopic(driver);
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

            // assert coding exists
            assertThat(obsCodes.hasCoding(fhirProperties.getSystems()
                .getLaboratorySystem(), "NA")).isTrue();
        }
    }

    @Test
    public void hl7streamFiltersNull() throws HL7Exception, IOException {
        // build stream
        try (var driver = buildHl7Stream(processor.hl7())) {

            var labTopic = createHl7InputTopic(driver);
            var outputTopic = createOutputTopic(driver);

            var msg = new ORU_R01();
            msg.initQuickstart("ORU", "R01", "P");

            // create input record
            labTopic.pipeInput("hl7-msg", msg);

            // get record from output topic
            var outputRecords = outputTopic.readRecordsToList();

            // assert filtered
            assertThat(outputRecords).isEmpty();

        }

    }
}
