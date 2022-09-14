package de.unimarburg.diz.labtofhir.serializer;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.util.StdConverter;
import org.hl7.fhir.r4.model.Observation;

public class ObservationStringConverter extends StdConverter<Observation, String> {

    private static final FhirContext fhirContext = FhirContext.forR4();

    @Override
    public String convert(Observation value) {
        return fhirContext
            .newJsonParser()
            .encodeResourceToString(value);
    }
}
