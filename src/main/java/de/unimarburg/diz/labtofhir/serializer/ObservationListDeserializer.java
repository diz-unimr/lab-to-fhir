package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.hl7.fhir.r4.model.Observation;

public class ObservationListDeserializer extends JsonDeserializer<List<Observation>> {

    private static final FhirContext fhirContext = FhirContext.forR4();
    private static final ObjectMapper mapper = new ObjectMapper();


    @Override
    public List<Observation> deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {

        var parser = fhirContext.newJsonParser();

        var valueAsString = p.getValueAsString();
        if (valueAsString == null) {
            return List.of();
        }

        var node = mapper.readTree(valueAsString);

        return StreamSupport.stream(node.spliterator(), false)
            .map(JsonNode::toString).map(s -> parser.parseResource(Observation.class, s))
            .collect(Collectors.toList());
    }
}
