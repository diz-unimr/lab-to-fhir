package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.MappingConfiguration;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.validator.FhirProfileValidator;
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
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("CheckStyle")
@SpringBootTest(classes = {AimLabMapper.class, FhirConfiguration.class,
    MappingConfiguration.class})
public class AimLabMapperTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("classpath:reports/1-diagnostic-report.json")
    private Resource testReport;

    @Value("classpath:reports/1-observations.json")
    private Resource testObservations;

    @Autowired
    private AimLabMapper mapper;
    @Autowired
    private FhirContext fhirContext;

    private static Stream<Arguments> parseRefRangeCases() {
        return Stream.of(
            Arguments.of("- <90", null, new SimpleQuantity().setValue(90.0)),
            Arguments.of(">10-", new SimpleQuantity().setValue(10.0), null),
            Arguments.of("text10-9", null, null),
            Arguments.of("1 - test", null, null)
        );
    }


    @Disabled("TODO move validation to processor tests")
    @Test
    public void resourceTypeIsValid() throws IOException {
        var validator = FhirProfileValidator.create(fhirContext);

        var report = getTestReport(testReport, testObservations);

        var bundle = mapper.apply(report);

        var validations = bundle.getEntry().stream()
            .map(x -> validator.validateWithResult(x.getResource()))
            .collect(Collectors.toList());

        validations.forEach(FhirProfileValidator::prettyPrint);

        assertThat(validations).allMatch(ValidationResult::isSuccessful);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    public void parseValueConvertsNumericWithComparator() {
        // arrange
        var report = createDummyReport();
        report.setObservations(List.of(
            new Observation().setValue(new StringType("<42")).setCode(
                new CodeableConcept().addCoding(new Coding().setCode("LEU")))));

        // act
        var result = mapper.apply(report);

        // assert
        var obs =
            result.getEntry().stream().map(BundleEntryComponent::getResource)
                .filter(Observation.class::isInstance)
                .map(Observation.class::cast).findFirst().orElseThrow();

        assertThat(obs.getValueQuantity()).usingRecursiveComparison()
            .ignoringExpectedNullFields()
            .isEqualTo(new Quantity(42.0)
                .setComparator(QuantityComparator.fromCode("<")));
    }

    private LaboratoryReport getTestReport(Resource testReport,
                                           Resource testObservations)
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
    public void bundleEntriesHaveRequestParameters() throws IOException {
        // arrange
        var report = getTestReport(testReport, testObservations);

        // act
        var result = mapper.apply(report);

        // assert entries
        assertThat(result.getEntry())
            .extracting(BundleEntryComponent::getRequest).allSatisfy(
                x -> assertThat(x).satisfies(
                        y -> assertThat(y.getMethod()).isEqualTo(HTTPVerb.PUT))
                    .satisfies(z -> assertThat(z.getUrl()).isNotBlank()));
    }

    @ParameterizedTest
    @MethodSource("parseRefRangeCases")
    void parseReferenceRangeSupportsSingleBounds(String textRange,
                                                 SimpleQuantity expectedLow,
                                                 SimpleQuantity expectedHigh) {

        var refRange = mapper.parseReferenceRange(textRange, new Quantity());

        // low
        if (expectedLow == null) {
            assertThat(refRange.hasLow()).isFalse();
        } else {
            assertThat(refRange.getLow()).usingRecursiveComparison()
                .isEqualTo(expectedLow);
        }
        // high
        if (expectedHigh == null) {
            assertThat(refRange.hasHigh()).isFalse();
        } else {
            assertThat(refRange.getHigh()).usingRecursiveComparison()
                .isEqualTo(expectedHigh);
        }
    }

    private LaboratoryReport createDummyReport() {
        var report = new LaboratoryReport();
        report.setResource(new DiagnosticReport()
            .addIdentifier(new Identifier().setValue("reportId")).setSubject(
                new Reference(new Patient().addIdentifier(
                    new Identifier().setValue("test")))).setEncounter(
                new Reference(new Encounter().addIdentifier(
                    new Identifier().setValue("encounterId"))))
            .setEffective(DateTimeType.now()));
        report.setObservations(List.of());
        return report;
    }
}
