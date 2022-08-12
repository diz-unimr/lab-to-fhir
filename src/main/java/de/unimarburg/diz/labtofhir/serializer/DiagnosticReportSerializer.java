package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import org.hl7.fhir.r4.model.DiagnosticReport;

public class DiagnosticReportSerializer extends StdSerializer<DiagnosticReport> {

    private static final FhirContext fhirContext = FhirContext.forR4();

    public DiagnosticReportSerializer() {
        super(DiagnosticReport.class);
    }

    @Override
    public void serialize(DiagnosticReport value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
        var valueString = fhirContext
            .newJsonParser()
            .encodeResourceToString(value);
        gen.writeString(valueString);
    }
}
