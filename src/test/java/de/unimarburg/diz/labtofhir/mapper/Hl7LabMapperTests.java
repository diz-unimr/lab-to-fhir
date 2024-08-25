package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {Hl7LabMapper.class, FhirConfiguration.class})
public class Hl7LabMapperTests {


    @Value("classpath:reports/test.hl7")
    private Resource testHl7;

    @Autowired
    private Hl7LabMapper mapper;

    @Test
    public void messageIsMapped() throws IOException, HL7Exception {

        var report = getTestReport(testHl7);

        var bundle = mapper.apply((ORU_R01) report);

        var mapped =
            bundle.getEntry().stream().map(BundleEntryComponent::getResource)
                .filter(ServiceRequest.class::isInstance)
                .map(ServiceRequest.class::cast).findFirst().orElseThrow();

        assertThat(mapped.getId()).isEqualTo("20220702_88888888");
    }

    private Message getTestReport(Resource testReport)
        throws IOException, HL7Exception {
        
        try (var ctx = new DefaultHapiContext()) {
            return ctx.getGenericParser()
                .parse(testReport.getContentAsString(StandardCharsets.UTF_8));
        }
    }

}
