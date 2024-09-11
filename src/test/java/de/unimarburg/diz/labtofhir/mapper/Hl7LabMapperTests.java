package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.KafkaConfiguration;
import org.apache.kafka.common.serialization.Serde;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {Hl7LabMapper.class, FhirConfiguration.class,
    KafkaConfiguration.class})
public class Hl7LabMapperTests {

    @Autowired
    private Serde<ORU_R01> hl7Serde;
    @Value("classpath:reports/test.hl7")
    private Resource testHl7;
    @Autowired
    private Hl7LabMapper mapper;

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
                .filter(Observation.class::isInstance).count()).isEqualTo(5);
    }

    private Message getTestReport(Resource testReport)
        throws IOException {

        return hl7Serde.deserializer()
            .deserialize(null, testReport.getContentAsByteArray());
    }

}
