package de.unimarburg.diz.labtofhir.serializer;

import org.hl7.fhir.r4.model.Observation;

public class ObservationDeserializer extends FhirDeserializer<Observation> {

    public ObservationDeserializer() {
        super(Observation.class);
    }
}
