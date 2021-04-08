package de.unimarburg.diz.labtofhir.serializer;

import org.hl7.fhir.r4.model.DiagnosticReport;

public class DiagnosticReportDeserializer extends FhirDeserializer<DiagnosticReport> {

    public DiagnosticReportDeserializer() {
        super(DiagnosticReport.class);
    }
}
