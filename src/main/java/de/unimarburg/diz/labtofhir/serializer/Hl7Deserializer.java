package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import org.apache.kafka.common.serialization.Deserializer;

import java.nio.charset.StandardCharsets;

public class Hl7Deserializer<T extends Message> implements Deserializer<T> {

    private final HapiContext ctx;

    public Hl7Deserializer() {
        this.ctx = new DefaultHapiContext();
        ctx.getParserConfiguration().setValidating(false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        try {
            return (T) ctx.getGenericParser()
                .parse(new String(data, StandardCharsets.UTF_8));
        } catch (HL7Exception e) {
            throw new RuntimeException(e);
        }

    }
}
