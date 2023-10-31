package de.unimarburg.diz.labtofhir.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import de.unimarburg.diz.labtofhir.model.LabFhirContext;
import java.io.IOException;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;

/*

 */
public class ObservationStringListDeserializer<T extends Resource> extends
    StdScalarDeserializer<Observation> {

    protected ObservationStringListDeserializer(Class<?> classType) {
        super(classType);
    }


    @Override
    public Observation deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {

        var parser = LabFhirContext
            .getInstance()
            .newJsonParser();

        var valueAsString = p.getValueAsString();
        if (valueAsString == null) {
            return null;
        }

        return parser.parseResource(Observation.class, valueAsString);
    }
}
