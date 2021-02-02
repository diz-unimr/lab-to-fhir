package de.unimarburg.diz.labtofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ContextConfiguration(classes = {MiiLabReportMapper.class, FhirConfiguration.class})
public class LabReportToFhirMapperTests {


    @Value("classpath:test-report.json")
    Resource testReport;
    @Autowired
    private MiiLabReportMapper mapper;
    @Autowired
    private FhirContext fhirContext;

    @Test
    public void mapperSetsPatient() throws IOException {
        var sourceResource = fhirContext.newJsonParser()
            .parseResource(DiagnosticReport.class, testReport.getInputStream());
        var sourceReport = new LaboratoryReport();
        sourceReport.setId(1);
        sourceReport.setResource(sourceResource);

        var targetBundle = new Bundle();

        mapper.setPatient(sourceReport, targetBundle);

        assertThat(targetBundle.getEntry()).extracting(BundleEntryComponent::getResource)
            .hasAtLeastOneElementOfType(Patient.class);

    }
}
