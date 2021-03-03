package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import org.hl7.fhir.r4.model.DiagnosticReport;

public class DiagnosticReportDeserializer extends JsonDeserializer<DiagnosticReport> {

    @Override
    public DiagnosticReport deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
        return FhirContext.forR4()
            .newJsonParser()
            .parseResource(DiagnosticReport.class, p.getValueAsString());
    }
}
