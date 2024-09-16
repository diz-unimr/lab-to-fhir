package de.unimarburg.diz.labtofhir.configuration;

import de.unimarburg.diz.labtofhir.mapper.DateFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = MappingConfiguration.class,
    properties = {
        "mapping.aim.filter=<2024-01-01",
        "mapping.hl7.filter=>=2024-01-01"})
public class MappingConfigurationTests {

    @Autowired
    @Qualifier("aimFilter")
    private DateFilter aimFilter;

    @Autowired
    @Qualifier("hl7Filter")
    private DateFilter hl7Filter;

    @Autowired
    @Qualifier("categoryMap")
    private Map<String, String> categoryMap;

    @Test
    void aimFilterIsSet() {
        assertThat(aimFilter.comparator()).isEqualTo("<");
        assertThat(aimFilter.date()).isEqualTo("2024-01-01");
    }

    @Test
    void hl7FilterIsSet() {
        assertThat(hl7Filter.comparator()).isEqualTo(">=");
        assertThat(hl7Filter.date()).isEqualTo("2024-01-01");
    }

    @Test
    void createFilterThrowsOnInvalidExpr() {
        assertThatThrownBy(() ->
            new MappingConfiguration().createDateFilter("2024-01-01")
        ).isInstanceOf(IllegalStateException.class)
            .hasMessage("Invalid filter expression: 2024-01-01");
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Test
    void categoryMapIsInitialized() {
        assertThat(categoryMap).hasSize(3039);
        assertThat(categoryMap.get("EBMA")).isEqualTo(
            "Infektionsdiagnostik Blut");
    }
}
