package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.KafkaConfiguration;
import de.unimarburg.diz.labtofhir.configuration.MappingConfiguration;
import de.unimarburg.diz.labtofhir.serializer.Hl7Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.TimeZone;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {Hl7LabMapper.class, FhirConfiguration.class,
    KafkaConfiguration.class, MappingConfiguration.class})
public class Hl7LabMapperTests {

    @Autowired
    private Serde<ORU_R01> hl7Serde;
    @Value("classpath:reports/test.hl7")
    private Resource testHl7;
    @Autowired
    private Hl7LabMapper mapper;

    private static Stream<Arguments> filterParams() {
        return Stream.of(
            Arguments.of("<", false),
            Arguments.of(">", false),
            Arguments.of("=", true),
            Arguments.of(">=", true),
            Arguments.of("<=", true)
        );
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    public void messageIsMapped() throws IOException {

        var report = getTestReport(testHl7);

        var bundle = mapper.apply((ORU_R01) report);

        var mappedReport =
            bundle.getEntry().stream().map(BundleEntryComponent::getResource)
                .filter(DiagnosticReport.class::isInstance)
                .map(DiagnosticReport.class::cast).findFirst().orElseThrow();

        assertThat(mappedReport.getId()).isEqualTo("20190417-55555555");
        // count resources
        assertThat(
            bundle.getEntry().stream().map(BundleEntryComponent::getResource)
                .filter(ServiceRequest.class::isInstance).count()).isEqualTo(1);
        assertThat(
            bundle.getEntry().stream().map(BundleEntryComponent::getResource)
                .filter(DiagnosticReport.class::isInstance).count()).isEqualTo(
            1);
        assertThat(
            bundle.getEntry().stream().map(BundleEntryComponent::getResource)
                .filter(Observation.class::isInstance).count()).isEqualTo(9);
    }

    private Message getTestReport(Resource testReport)
        throws IOException {

        return hl7Serde.deserializer()
            .deserialize(null, testReport.getContentAsByteArray());
    }

    @ParameterizedTest
    @MethodSource("filterParams")
    void createFilterTest(String comp, boolean expected)
        throws HL7Exception, IOException {
        var targetDate = LocalDateTime.parse("2022-02-22T22:22")
            .atZone(TimeZone.getDefault().toZoneId())
            .toInstant();
        var dateType = Date.from(targetDate);

        // test message
        var msg = new ORU_R01();
        msg.initQuickstart("ORU", "R01", "P");
        msg.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR()
            .getObservationDateTime()
            .getTimeOfAnEvent()
            .setValue(dateType);

        // create filter
        var filter = mapper.createFilter(
            new DateFilter(LocalDate.parse("2022-02-22"), comp));

        assertThat(filter.test(msg)).isEqualTo(expected);
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Test
    void parseValueMapsNumericRangeToString() throws HL7Exception {

        var msg = """
            MSH|^~\\&|SWISSLAB|KLIN|DBSERV||20220702120811|LAB|ORU^R01|test-msg.000000|P|2.2|||AL|NE\r
            OBR|1|20220702_88888888|||||20220702120811||||||||||||||||||F\r
            OBX|1|NM|CMG^Cytomegalievirus IgG||3-9|AU/ml| - <6||||F
            """;

        try (var deserializer = new Hl7Deserializer<>()) {
            var oru = (ORU_R01) deserializer.deserialize(null,
                msg.getBytes(StandardCharsets.UTF_8));
            var obx =
                oru.getPATIENT_RESULT().getORDER_OBSERVATION().getOBSERVATION()
                    .getOBX();
            var actual = mapper.parseValue(obx);

            assertThat(actual.primitiveValue()).isEqualTo("3-9");
        }
    }

    @SuppressWarnings({"checkstyle:LineLength", "checkstyle:MagicNumber"})
    @Test
    void duplicateObservationCodesAreMapped() throws HL7Exception {

        var msg = """
            MSH|^~\\&|SWISSLAB|KLIN|DBSERV||20240702120811|LAB|ORU^R01|test-msg.blubb|P|2.2|||AL|NE\r
            OBR|1|bla|||||20240702120811||||||||||||||||||F\r
            OBX|1|ST|HST^Harnstoff||folgt||||||I\r
            OBR|2|bla|||||20240702120811||||||||||||||||||F\r
            OBX|1|ST|HST^Harnstoff||folgt||||||I\r
            OBR|3|bla|||||20240702120811||||||||||||||||||F\r
            OBX|1|ST|HST^Harnstoff||folgt||||||I
            """;

        try (var deserializer = new Hl7Deserializer<>()) {
            var oru = (ORU_R01) deserializer.deserialize(null,
                msg.getBytes(StandardCharsets.UTF_8));
            var actual = mapper.mapObservations(oru);

            assertThat(actual).hasSize(3);
            // observations have different identifier values
            assertThat(actual).extracting(
                    o -> o.getIdentifierFirstRep().getValue())
                .doesNotHaveDuplicates();
        }
    }

}
