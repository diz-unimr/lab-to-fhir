package de.unimarburg.diz.labtofhir.configuration;

import javax.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


@ConfigurationProperties(prefix = "fhir")
@Validated
public class FhirProperties {

    private final Systems systems = new Systems();
    @NotNull
    private Boolean generateNarrative;

    public Systems getSystems() {
        return systems;
    }

    public Boolean getGenerateNarrative() {
        return generateNarrative;
    }

    public void setGenerateNarrative(Boolean generateNarrative) {
        this.generateNarrative = generateNarrative;
    }

    public static class Systems {


        @NotNull
        private String serviceRequestId;
        @NotNull
        private String diagnosticReportId;
        @NotNull
        private String observationId;
        @NotNull
        private String patientId;
        @NotNull
        private String encounterId;
        @NotNull
        private String assignerId;
        @NotNull
        private String laboratorySystem;

        public String getLaboratorySystem() {
            return laboratorySystem;
        }

        public void setLaboratorySystem(String laboratorySystem) {
            this.laboratorySystem = laboratorySystem;
        }

        public String getServiceRequestId() {
            return serviceRequestId;
        }

        public void setServiceRequestId(String serviceRequestId) {
            this.serviceRequestId = serviceRequestId;
        }

        public String getDiagnosticReportId() {
            return diagnosticReportId;
        }

        public void setDiagnosticReportId(String diagnosticReportId) {
            this.diagnosticReportId = diagnosticReportId;
        }

        public String getObservationId() {
            return observationId;
        }

        public void setObservationId(String observationId) {
            this.observationId = observationId;
        }

        public String getPatientId() {
            return patientId;
        }

        public void setPatientId(String patientId) {
            this.patientId = patientId;
        }

        public String getEncounterId() {
            return encounterId;
        }

        public void setEncounterId(String encounterId) {
            this.encounterId = encounterId;
        }

        public String getAssignerIdSystem() {
            return assignerId;
        }

        public void setAssignerIdSystem(String assignerId) {
            this.assignerId = assignerId;
        }

        public String getAssignerId() {
            return assignerId;
        }

        public void setAssignerId(String assignerId) {
            this.assignerId = assignerId;
        }
    }
}
