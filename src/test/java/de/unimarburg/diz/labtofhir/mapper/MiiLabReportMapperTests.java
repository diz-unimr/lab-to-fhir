package de.unimarburg.diz.labtofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.validator.FhirProfileValidator;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
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
public class MiiLabReportMapperTests {


    @Value("classpath:test-report.json")
    Resource testReport;
    @Autowired
    private MiiLabReportMapper mapper;
    @Autowired
    private FhirContext fhirContext;


    @Test
    public void resourceTypeIsValid() throws IOException {
        var validator = FhirProfileValidator.create(fhirContext);

        var report = getTestReport();

        var bundle = mapper.apply(report);

        assertThat(bundle.getEntry()).extracting(BundleEntryComponent::getResource)
            .allMatch(r -> validator.validateWithResult(r)
                .isSuccessful());
    }

    private LaboratoryReport getTestReport() throws IOException {
        var resource = fhirContext.newJsonParser()
            .parseResource(DiagnosticReport.class, testReport.getInputStream());
        var report = new LaboratoryReport();
        report.setId(1);
        report.setResource(resource);
        return report;
    }

    private LaboratoryReport createDummyReport() {
        var report = new LaboratoryReport();
        report.setResource(
            new DiagnosticReport().addIdentifier(new Identifier().setValue("reportId"))
                .setSubject(
                    new Reference(new Patient().addIdentifier(new Identifier().setValue("test"))))
                .setEncounter(new Reference(
                    new Encounter().addIdentifier(new Identifier().setValue("encounterId"))))
                .setEffective(DateTimeType.now()));
        return report;
    }
}
