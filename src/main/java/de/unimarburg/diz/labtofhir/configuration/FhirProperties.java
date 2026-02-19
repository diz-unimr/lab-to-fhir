package de.unimarburg.diz.labtofhir.configuration;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "fhir")
@Validated
public final class FhirProperties {

    private final Systems systems = new Systems();

    private final Profile profile = new Profile();

    public Profile getProfile() {
        return profile;
    }

    public Systems getSystems() {
        return systems;
    }

    @Setter
    @Getter
    public static final class Systems {

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

    }

    @Setter
    @Getter
    public static final class Profile {
        @NotNull
        private String observation;
        @NotNull
        private String serviceRequest;
        @NotNull
        private String diagnosticReport;

    }
}
