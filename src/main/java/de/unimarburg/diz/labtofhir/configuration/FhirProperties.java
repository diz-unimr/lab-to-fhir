package de.unimarburg.diz.labtofhir.configuration;

public class FhirProperties {

    private final String serviceRequestSystem;
    private final String diagnosticReportSystem;
    private final String observationIdSystem;
    private final String patientIdSystem;
    private final String encounterIdSystem;
    // TODO ...

    public FhirProperties(String serviceRequestSystem, String diagnosticReportSystem,
        String observationIdSystem, String patientIdSystem, String encounterIdSystem) {
        this.serviceRequestSystem = serviceRequestSystem;
        this.diagnosticReportSystem = diagnosticReportSystem;
        this.observationIdSystem = observationIdSystem;
        this.patientIdSystem = patientIdSystem;
        this.encounterIdSystem = encounterIdSystem;
    }

    public String getServiceRequestSystem() {
        return serviceRequestSystem;
    }

    public String getDiagnosticReportSystem() {
        return diagnosticReportSystem;
    }

    public String getObservationIdSystem() {
        return observationIdSystem;
    }

    public String getPatientIdSystem() {
        return patientIdSystem;
    }

    public String getEncounterIdSystem() {
        return encounterIdSystem;
    }


}
