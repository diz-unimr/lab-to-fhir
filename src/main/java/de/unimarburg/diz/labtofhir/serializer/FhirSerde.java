package de.unimarburg.diz.labtofhir.serializer;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;
import org.hl7.fhir.r4.model.Resource;

public class FhirSerde<T extends Resource> implements Serde<T> {

    private Serializer<T> serializer;
    private Deserializer<T> deserializer;

    public FhirSerde(Class<T> classType) {
        this.serializer = new FhirSerializer<T>();
        this.deserializer = new FhirDeserializer<T>(classType);
    }

    @Override
    public Serializer<T> serializer() {
        return serializer;
    }

    @Override
    public Deserializer<T> deserializer() {
        return deserializer;
    }
}
