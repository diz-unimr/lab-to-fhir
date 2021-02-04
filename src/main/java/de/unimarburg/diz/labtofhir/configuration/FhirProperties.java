package de.unimarburg.diz.labtofhir.configuration;

public class FhirProperties {

    private final String serviceRequestSystem;
    private final String diagnosticReportSystem;
    private final String observationIdSystem;
    private final String patientIdSystem;
    private final String encounterIdSystem;
    private final String assignerIdSystem;
    private final String assignerIdCode;
    private final Boolean generateNarrative;

    public FhirProperties(String serviceRequestSystem, String diagnosticReportSystem,
        String observationIdSystem, String patientIdSystem, String encounterIdSystem,
        String assignerIdSystem, String assignerIdCode, Boolean generateNarrative) {
        this.serviceRequestSystem = serviceRequestSystem;
        this.diagnosticReportSystem = diagnosticReportSystem;
        this.observationIdSystem = observationIdSystem;
        this.patientIdSystem = patientIdSystem;
        this.encounterIdSystem = encounterIdSystem;
        this.assignerIdSystem = assignerIdSystem;
        this.assignerIdCode = assignerIdCode;
        this.generateNarrative = generateNarrative;
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

    public String getAssignerIdSystem() {
        return assignerIdSystem;
    }

    public String getAssignerIdCode() {
        return assignerIdCode;
    }

    public Boolean getGenerateNarrative() {
        return generateNarrative;
    }


}
