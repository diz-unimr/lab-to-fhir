package de.unimarburg.diz.labtofhir.model;

import ca.uhn.fhir.context.FhirContext;

public class LabFhirContext {

    private static final FhirContext fhirContext = FhirContext.forR4();

    public static FhirContext getInstance() {
        return fhirContext;
    }
}
