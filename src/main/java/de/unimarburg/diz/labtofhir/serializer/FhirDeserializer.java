package de.unimarburg.diz.labtofhir.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import de.unimarburg.diz.labtofhir.model.LabFhirContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.kafka.common.serialization.Deserializer;
import org.hl7.fhir.r4.model.Resource;

public class FhirDeserializer<T extends Resource> extends JsonDeserializer<T> implements
    Deserializer<T> {

    private final Class<T> classType;

    public FhirDeserializer(Class<T> classType) {
        this.classType = classType;
    }


    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        return LabFhirContext
            .getInstance()
            .newJsonParser()
            .parseResource(classType, new ByteArrayInputStream(data));
    }

    @Override
    public T deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        return deserialize(p.getValueAsString());
    }

    public T deserialize(String value) {
        return LabFhirContext
            .getInstance()
            .newJsonParser()
            .parseResource(classType, value);
    }

    @Override
    public Class<?> handledType() {
        return classType;
    }
}
