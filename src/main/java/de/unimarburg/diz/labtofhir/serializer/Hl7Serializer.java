package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import org.apache.kafka.common.serialization.Serializer;

public class Hl7Serializer<T extends Message> implements Serializer<T> {

    private final HapiContext ctx;

    public Hl7Serializer() {
        this.ctx = new DefaultHapiContext();
    }


    @Override
    public byte[] serialize(String topic, T data) {
        try {
            return ctx.getGenericParser()
                .encode(data)
                .getBytes();
        } catch (HL7Exception e) {
            throw new RuntimeException(e);
        }
    }
}
