package de.unimarburg.diz.labtofhir.model;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Identifier;
import org.junit.jupiter.api.Test;

public class LaboratoryReportTests {

    @Test
    void setResourceStripsIdentifierPrefix() {
        var source = new DiagnosticReport().addIdentifier(
            new Identifier().setValue("SWISSLAB_20240710_test"));
        var expectedId = "20240710_test";

        var labReport = new LaboratoryReport();
        labReport.setResource(source);

        assertThat(labReport.getReportIdentifierValue()).isEqualTo(expectedId);
    }
}
