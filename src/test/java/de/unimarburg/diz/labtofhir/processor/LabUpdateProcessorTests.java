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
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.test.TestRecord;
import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {LabUpdateProcessor.class, MiiLabReportMapper.class,
    LoincMapper.class, FhirConfiguration.class, MappingConfiguration.class,
    KafkaConfig.class})
@TestPropertySource(properties = {"mapping.loinc.version=''",
    "mapping.loinc.credentials.user=''",
    "mapping.loinc.credentials.password=''",
    "mapping.loinc.local=mapping-swl-loinc.zip"})
public class LabUpdateProcessorTests extends BaseProcessorTests {

    @Autowired
    private LabUpdateProcessor processor;


    @Autowired
    private FhirProperties fhirProperties;

    @SuppressWarnings({"checkstyle:MagicNumber", "checkstyle:LineLength"})
    @Test
    void updateIsProcessed() {

        // build stream
        try (var driver = buildStream(processor.update())) {

            var labTopic = createInputTopic(driver);
            var outputTopic = createOutputTopic(driver);

            // NA values are updated, but update only processes the first two
            // because the last one (4) is where the default processor picks up
            // according to processOffsets()
            var inputReports =
                List.of(createReport(1, "NA"), createReport(2, "ERY"),
                    createReport(3, "NA"), createReport(4, "NA"));

            // create input records
            labTopic.pipeKeyValueList(inputReports.stream()
                .map(r -> new KeyValue<>(String.valueOf(r.getId()), r))
                .toList());

            // get record from output topic
            var outputRecords = outputTopic.readRecordsToList();

            // expected keys are: 1, 3
            assertThat(outputRecords.stream().map(TestRecord::getKey)
                .toList()).isEqualTo(List.of("1", "3"));

            // assert codes are mapped
            var obsCodes = getObservationsCodes(outputRecords).toList();

            // all updated observations have LOINC coding for NA
            assertThat(obsCodes).allMatch(
                codes -> codes.hasCoding("http://loinc.org", "2951-2"));
        }
    }

    private LaboratoryReport createReport(int reportId, String labCode) {
        return createReport(reportId, new Coding()
            .setSystem(fhirProperties.getSystems().getLaboratorySystem())
            .setCode(labCode));
    }

    @TestConfiguration
    static class KafkaConfig {

        @SuppressWarnings("checkstyle:MagicNumber")
        @Bean
        LabOffsets testOffsets() {
            // offset target will be 3 on partition 0
            return new LabOffsets(Map.of(0, new OffsetAndMetadata(3L)),
                Map.of());
        }

        @Bean
        MappingInfo testMappingInfo() {
            return new MappingInfo(new MappingUpdate(null, null, List.of("NA")),
                false);
        }
    }
}
