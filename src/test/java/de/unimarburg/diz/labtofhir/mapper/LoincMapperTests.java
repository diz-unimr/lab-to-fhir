package de.unimarburg.diz.labtofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.configuration.MappingConfiguration;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;


@SpringBootTest
@ContextConfiguration(classes = {LoincMapper.class, MappingConfiguration.class})
@TestPropertySource(properties = {"mapping.loinc.local=mapping-swl-loinc-test.zip"})
public class LoincMapperTests {

    @Autowired
    private LoincMapper loincMapper;

    private static Stream<Arguments> mapCodeAndQuantityProvidesMetaCodeArguments() {
        return Stream.of(
            Arguments.of("TEST", null, "1000-0"),
            Arguments.of("TEST", "meta1", "1000-1"),
            Arguments.of("TEST", "meta2", "1000-2")
        );
    }

    @Test
    public void loincMapIsInitialized() {

        assertThat(loincMapper.hasMappings()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("mapCodeAndQuantityProvidesMetaCodeArguments")
    public void mapCodeAndQuantityProvidesMetaCode(String code, String metaCode,
        String expectedMappedCode) {

        var obs = new Observation().setCode(
                new CodeableConcept().addCoding(new Coding().setCode(code)))
            .setValue(new Quantity());
        loincMapper.mapCodeAndQuantity(obs, metaCode);

        assertThat(obs.getCode().getCodingFirstRep().getCode()).isEqualTo(expectedMappedCode);
    }
}
