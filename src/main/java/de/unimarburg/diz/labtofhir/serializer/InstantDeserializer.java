package de.unimarburg.diz.labtofhir.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;

public class InstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
        long value = parser.getValueAsLong();

        return Instant.ofEpochMilli(value)
            .atZone(ZoneId.systemDefault())
            .toInstant();

    }
}
