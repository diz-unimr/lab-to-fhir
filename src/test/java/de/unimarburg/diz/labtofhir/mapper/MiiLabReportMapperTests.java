package de.unimarburg.diz.labtofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.validator.FhirProfileValidator;
import java.io.IOException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ContextConfiguration(classes = {MiiLabReportMapper.class, FhirConfiguration.class,
    LoincMapper.class})
@TestPropertySource(
    properties = {
        "mapping.loinc.file=mapping_swl_loinc-v1.1.csv",
    })
public class MiiLabReportMapperTests {


    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("classpath:reports/1-diagnostic-report.json")
    Resource testReport;
    @Value("classpath:reports/1-observations.json")
    Resource testObservations;
    @Autowired
    private MiiLabReportMapper mapper;
    @Autowired
    private FhirContext fhirContext;

    @Test
    public void resourceTypeIsValid() throws IOException {
        var validator = FhirProfileValidator.create(fhirContext);

        var report = getTestReport(testReport, testObservations);

        var bundle = mapper.apply(report).getValue();

        var validations = bundle.getEntry()
            .stream()
            .map(x -> validator.validateWithResult(x.getResource()))
            .collect(Collectors.toList());

        validations.forEach(FhirProfileValidator::prettyPrint);

        assertThat(validations).allMatch(ValidationResult::isSuccessful);
    }

    private LaboratoryReport getTestReport(Resource testReport,
        Resource testObservations) throws IOException {
        var parser = fhirContext.newJsonParser();
        var diagnosticReport = parser
            .parseResource(DiagnosticReport.class, testReport.getInputStream());

        var node = objectMapper.readTree(testObservations.getInputStream());
        var observations = StreamSupport.stream(node.spliterator(), false)
            .map(JsonNode::toString).map(s -> parser.parseResource(Observation.class, s))
            .collect(Collectors.toList());

        var report = new LaboratoryReport();
        report.setId(1);
        report.setResource(diagnosticReport);
        report.setObservations(observations);
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
