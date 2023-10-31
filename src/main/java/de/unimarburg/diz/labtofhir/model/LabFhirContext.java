package de.unimarburg.diz.labtofhir.model;

import ca.uhn.fhir.context.FhirContext;

public class LabFhirContext {

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    public static FhirContext getInstance() {
        return FHIR_CONTEXT;
    }
}
