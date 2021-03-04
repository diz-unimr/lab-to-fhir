package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import org.hl7.fhir.r4.model.DiagnosticReport;

public class DiagnosticReportDeserializer extends JsonDeserializer<DiagnosticReport> {

    // TODO inject context
    private final FhirContext fhirContext;

    public DiagnosticReportDeserializer() {
        fhirContext = FhirContext.forR4();
    }

    @Override
    public DiagnosticReport deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
        return fhirContext.newJsonParser()
            .parseResource(DiagnosticReport.class, p.getValueAsString());
    }
}
