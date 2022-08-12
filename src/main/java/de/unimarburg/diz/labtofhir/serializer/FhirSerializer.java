package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.fhir.context.FhirContext;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.serialization.Serializer;
import org.hl7.fhir.r4.model.Resource;

public class FhirSerializer<T extends Resource> implements Serializer<T> {

    private static final FhirContext fhirContext = FhirContext.forR4();

    @Override
    public byte[] serialize(String topic, T data) {
        if (data == null) {
            return null;
        }

        return fhirContext
            .newJsonParser()
            .encodeResourceToString(data)
            .getBytes(StandardCharsets.UTF_8);
    }


}
