package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.util.StdConverter;
import org.hl7.fhir.r4.model.Observation;

public class StringObservationConverter extends StdConverter<String, Observation> {

    private static final FhirContext fhirContext = FhirContext.forR4();


    @Override
    public Observation convert(String value) {
        return fhirContext
            .newJsonParser()
            .parseResource(Observation.class, value);
    }
}

