package de.unimarburg.diz.labtofhir.processor;

import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.mapper.AimLabMapper;
import de.unimarburg.diz.labtofhir.mapper.Hl7LabMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ContextConfigurationTests {

    private final ApplicationContextRunner contextRunner
            = new ApplicationContextRunner().withUserConfiguration(LabToFhirProcessor.class, AimLabMapper.class, Hl7LabMapper.class, FhirConfiguration.class);

    @Test
    void failsOnMissingMapper() {
        Throwable t = assertThrows(IllegalStateException.class, () -> this.contextRunner
                .withPropertyValues("mapping.aim.enabled=false", "mapping.hl7.enabled=false")
                .run((context) -> context.getBean(LabToFhirProcessor.class)));
        assertThat(t).hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("No mapper configured! Set either {mapper.aim.enabled} or {mapper.hl7.enabled} to 'true'");
    }

    @Test
    void aimIsDisabled() {
        this.contextRunner
                .withPropertyValues("mapping.aim.enabled=false", "mapping.hl7.enabled=true")
                .run((context) -> assertThat(context).doesNotHaveBean(AimLabMapper.class));
    }

    @Test
    void hl7IsDisabled() {
        this.contextRunner
                .withPropertyValues("mapping.aim.enabled=true", "mapping.hl7.enabled=false")
                .run((context) -> assertThat(context).doesNotHaveBean(Hl7LabMapper.class));
    }
}
