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

    @Test
    void getValidReportIdReplacesInvalidCharacters() {
        var source = new DiagnosticReport().addIdentifier(
            new Identifier().setValue("SWISSLAB_20240710_123456"));
        var expectedId = "20240710-123456";

        var labReport = new LaboratoryReport();
        labReport.setResource(source);

        assertThat(labReport.getValidReportId()).isEqualTo(expectedId);
    }

    @Test
    void getValidReportIdTrimsByMaxLength() {
        var source =
            new DiagnosticReport().addIdentifier(new Identifier().setValue(
                // 70 characters
                "111111111122222222223333333333444444444455555555556666666666"
                    + "7777777777"));
        // only 64 characters are valid
        var expectedId =
            "1111111111222222222233333333334444444444555555555566666666667777";

        var labReport = new LaboratoryReport();
        labReport.setResource(source);

        assertThat(labReport.getValidReportId()).isEqualTo(expectedId);
    }
}
