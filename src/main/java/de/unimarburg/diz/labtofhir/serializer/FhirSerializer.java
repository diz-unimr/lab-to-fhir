package de.unimarburg.diz.labtofhir.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import de.unimarburg.diz.labtofhir.model.LabFhirContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.serialization.Serializer;
import org.hl7.fhir.r4.model.Resource;

public class FhirSerializer<T extends Resource> extends JsonSerializer<T> implements Serializer<T> {
    
    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) {
            return null;
        }

        return LabFhirContext
            .getInstance()
            .newJsonParser()
            .encodeResourceToString(data)
            .getBytes(StandardCharsets.UTF_8);
    }


    @Override
    public void serialize(T value, JsonGenerator gen, SerializerProvider provider)
        throws IOException {
        var valueString = LabFhirContext
            .getInstance()
            .newJsonParser()
            .encodeResourceToString(value);
        gen.writeString(valueString);
    }
}
