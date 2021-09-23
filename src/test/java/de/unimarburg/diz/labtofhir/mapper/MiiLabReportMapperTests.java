package de.unimarburg.diz.labtofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.MappingConfiguration;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.validator.FhirProfileValidator;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Quantity.QuantityComparator;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ContextConfiguration(classes = {MiiLabReportMapper.class, FhirConfiguration.class,
    MappingConfiguration.class, LoincMapper.class})
@TestPropertySource(properties = {"mapping.loinc.local=mapping-swl-loinc.zip"})
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

    private static Stream<Arguments> metaCodesAreSetAndFilteredArgs() {
        return MiiLabReportMapper.metaCodes.stream().map(Arguments::of);
    }

    @Test
    public void resourceTypeIsValid() throws IOException {
        var validator = FhirProfileValidator.create(fhirContext);

        var report = getTestReport(testReport, testObservations);

        var bundle = mapper.apply(report)
            .getValue();

        var validations = bundle.getEntry()
            .stream()
            .map(x -> validator.validateWithResult(x.getResource()))
            .collect(Collectors.toList());

        validations.forEach(FhirProfileValidator::prettyPrint);

        assertThat(validations).allMatch(ValidationResult::isSuccessful);
    }

    @Test
    public void parseValueConvertsNumericWithComparator() {
        // arrange
        var report = createDummyReport();
        report.setObservations(List.of(new Observation().setValue(new StringType("<42"))
            .setCode(new CodeableConcept().addCoding(new Coding().setCode("LEU")))));

        // act
        var result = mapper.apply(report);

        // assert
        var obs = result.getValue()
            .getEntry()
            .stream()
            .map(BundleEntryComponent::getResource)
            .filter(Observation.class::isInstance)
            .map(Observation.class::cast)
            .findFirst()
            .orElseThrow();

        assertThat(obs.getValueQuantity()).usingRecursiveComparison()
            .ignoringExpectedNullFields()
            .isEqualTo(new Quantity(42.0).setComparator(QuantityComparator.fromCode("<")));
    }

    @ParameterizedTest
    @MethodSource("metaCodesAreSetAndFilteredArgs")
    public void metaCodesAreSetAndFiltered(String code) {
        var report = createDummyReport();
        report.setObservations(List.of(new Observation().setCode(
                new CodeableConcept().addCoding(new Coding().setCode(code)))
            .setValue(new StringType("metaCode"))));
        setReportResult(report);

        // act
        mapper.apply(report);

        // assert
        assertThat(report.getMetaCode()).isEqualTo("metaCode");
        assertThat(report.getResource().getResult()).isEmpty();
    }

    private LaboratoryReport getTestReport(Resource testReport, Resource testObservations)
        throws IOException {
        var parser = fhirContext.newJsonParser();
        var diagnosticReport = parser.parseResource(DiagnosticReport.class,
            testReport.getInputStream());

        var node = objectMapper.readTree(testObservations.getInputStream());
        var observations = StreamSupport.stream(node.spliterator(), false)
            .map(JsonNode::toString)
            .map(s -> parser.parseResource(Observation.class, s))
            .collect(Collectors.toList());

        var report = new LaboratoryReport();
        report.setId(1);
        report.setResource(diagnosticReport);
        report.setObservations(observations);
        return report;
    }

    @Test
    public void BundleEntriesHaveRequestParameters() throws IOException {
        // arrange
        var report = getTestReport(testReport, testObservations);

        // act
        var result = mapper.apply(report);

        // assert entries
        assertThat(result.getValue()
            .getEntry()).extracting(BundleEntryComponent::getRequest)
            .allSatisfy(
                x -> assertThat(x).satisfies(y -> assertThat(y.getMethod()).isEqualTo(HTTPVerb.PUT))
                    .satisfies(z -> assertThat(z.getUrl()).isNotBlank()));
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
        report.setObservations(List.of());
        return report;
    }

    private void setReportResult(LaboratoryReport report) {
        report.getResource()
            .setResult(report.getObservations().stream().map(Reference::new).collect(
                Collectors.toList()));
    }
}
