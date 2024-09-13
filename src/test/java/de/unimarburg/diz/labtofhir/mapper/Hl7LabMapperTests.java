package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.KafkaConfiguration;
import de.unimarburg.diz.labtofhir.configuration.MappingConfiguration;
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
                .filter(Observation.class::isInstance).count()).isEqualTo(8);
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

}
