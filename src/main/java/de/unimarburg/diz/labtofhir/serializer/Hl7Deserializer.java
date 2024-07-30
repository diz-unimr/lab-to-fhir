package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.model.Message;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.serialization.Deserializer;

public class Hl7Deserializer implements Deserializer<Message> {

    private final HapiContext ctx;

    public Hl7Deserializer() {
        this.ctx = new DefaultHapiContext();
    }

    @Override
    public Message deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }

        try {
            return ctx.getPipeParser()
                .parse(new String(data, StandardCharsets.UTF_8));
        } catch (HL7Exception e) {
            return null;
        }
    }
}
