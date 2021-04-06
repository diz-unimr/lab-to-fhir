package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import org.hl7.fhir.r4.model.DiagnosticReport;

public class DiagnosticReportSerializer extends JsonSerializer<DiagnosticReport> {

    private final IParser jsonParser;

    public DiagnosticReportSerializer() {
        jsonParser = FhirContext.forR4().newJsonParser();
    }

    @Override
    public void serialize(DiagnosticReport value, JsonGenerator gen,
        SerializerProvider serializers) throws IOException {
        var valueString = jsonParser.encodeResourceToString(value);
        gen.writeString(valueString);
    }
}
