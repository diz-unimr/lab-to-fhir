package de.unimarburg.diz.labtofhir.configuration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "fhir")
@Validated
public class FhirProperties {

    private final Systems systems = new Systems();

    public Systems getSystems() {
        return systems;
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
        private String assignerCode;
        @NotNull
        private String laboratorySystem;
        @NotNull
        private String laboratoryUnitSystem;
        @NotNull
        private String mapperTagSystem;

        public String getMapperTagSystem() {
            return mapperTagSystem;
        }

        public void setMapperTagSystem(String mapperTagSystem) {
            this.mapperTagSystem = mapperTagSystem;
        }

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

        public String getAssignerId() {
            return assignerId;
        }

        public void setAssignerId(String assignerId) {
            this.assignerId = assignerId;
        }

        public String getAssignerCode() {
            return assignerCode;
        }

        public void setAssignerCode(String assignerCode) {
            this.assignerCode = assignerCode;
        }

        public String getLaboratoryUnitSystem() {
            return this.laboratoryUnitSystem;
        }

        public void setLaboratoryUnitSystem(String laboratoryUnitSystem) {
            this.laboratoryUnitSystem = laboratoryUnitSystem;
        }
    }
}
