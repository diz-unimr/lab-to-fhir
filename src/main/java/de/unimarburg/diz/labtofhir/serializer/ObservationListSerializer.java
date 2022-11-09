package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Observation;

public class ObservationListSerializer extends JsonSerializer<List<Observation>> {

    private static final FhirContext fhirContext = FhirContext.forR4();

    @Override
    public void serialize(List<Observation> value, JsonGenerator gen,
        SerializerProvider serializers) throws IOException {

        var jsonParser = fhirContext.newJsonParser();

        var serialized = value
            .stream()
            .map(jsonParser::encodeResourceToString)
            .collect(Collectors.joining(","));
        gen.writeString("[" + serialized + "]");
    }
}
