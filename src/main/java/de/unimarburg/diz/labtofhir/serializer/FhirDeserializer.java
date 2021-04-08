package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import org.hl7.fhir.r4.model.Resource;

public class FhirDeserializer<T extends Resource> extends JsonDeserializer<T> {

    private static final FhirContext fhirContext = FhirContext.forR4();
    private final Class<T> classType;

    public FhirDeserializer(Class<T> classType) {
        this.classType = classType;
    }


    @Override
    public T deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
        return deserialize(classType, p, ctx);
    }

    public T deserialize(Class<T> classType, JsonParser p, DeserializationContext ctx)
        throws IOException {
        return fhirContext.newJsonParser()
            .parseResource(classType, p.getValueAsString());
    }

    @Override
    public Class<?> handledType() {
        return classType;
    }
}
