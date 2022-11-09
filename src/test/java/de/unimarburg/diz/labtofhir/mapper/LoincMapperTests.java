package de.unimarburg.diz.labtofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.configuration.FhirConfiguration;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.LoincMap;
import de.unimarburg.diz.labtofhir.model.LoincMapEntry;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationReferenceRangeComponent;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest(classes = {LoincMapper.class, FhirConfiguration.class})
public class LoincMapperTests {

    @Autowired
    private LoincMapper loincMapper;

    @Autowired
    private FhirProperties fhirProperties;
    private static LoincMap testLoincMap;

    private static Stream<Arguments> mapCodeAndQuantityProvidesMetaCodeArguments() {
        return Stream.of(Arguments.of("TEST", null, "1000-0"),
            Arguments.of("TEST", "meta1", "1000-1"), Arguments.of("TEST", "meta2", "1000-2"),
            // fallback to null source
            Arguments.of("TEST", "not-mapped", "1000-0"));
    }

    @BeforeAll
    public static void init() {
        testLoincMap = new LoincMap()
            .setSwl("TEST")
            .setEntries(List.of(new LoincMapEntry()
                .setLoinc("1000-0")
                .setUcum("mmol/L"), new LoincMapEntry()
                .setMeta("meta1")
                .setLoinc("1000-1")
                .setUcum("mmol/L"), new LoincMapEntry()
                .setMeta("meta2")
                .setLoinc("1000-2")
                .setUcum("mmol/L")));
    }


    @ParameterizedTest
    @MethodSource("mapCodeAndQuantityProvidesMetaCodeArguments")
    public void mapCodeAndQuantityProvidesMetaCode(String code, String metaCode,
        String expectedMappedCode) {

        var obs = new Observation()
            .setCode(new CodeableConcept()
                .addCoding(new Coding()
                    .setSystem(fhirProperties
                        .getSystems()
                        .getLaboratorySystem())
                    .setCode(code))
                .addCoding(new Coding(fhirProperties
                    .getSystems()
                    .getLabReportMetaSystem(), metaCode, null)))
            .setValue(new Quantity());
        loincMapper.apply(new Bundle().addEntry(new BundleEntryComponent().setResource(obs)),
            testLoincMap);

        assertThat(obs
            .getCode()
            .getCodingFirstRep()
            .getCode()).isEqualTo(expectedMappedCode);
    }

    @Test
    public void ucumUnitsAreMapped() {
        // arrange
        var oldUnitQuantity = new Quantity().setUnit("old unit");
        var obs = new Observation()
            .setCode(new CodeableConcept().addCoding(new Coding(fhirProperties
                .getSystems()
                .getLaboratorySystem(), "TEST", null)))
            .setValue(oldUnitQuantity)
            .addReferenceRange(new ObservationReferenceRangeComponent()
                .setLow(oldUnitQuantity)
                .setHigh(oldUnitQuantity));
        //        var loincMap = new LoincMap()
        //            .setSwl("TEST")
        //            .setEntries(List.of(new LoincMapEntry()
        //                .setLoinc("1000-1")
        //                .setUcum("mmol/L")));

        // act
        loincMapper.apply(
            new Bundle().setEntry(List.of(new BundleEntryComponent().setResource(obs))),
            testLoincMap);

        // assert value has new unit and code
        assertThat(obs.getValueQuantity())
            .extracting(Quantity::getUnit, Quantity::getUnit)
            .containsOnly("mmol/L");
        // .. as well as reference range unit and code
        assertThat(obs.getReferenceRange())
            .flatExtracting(x -> x
                .getLow()
                .getUnit(), x -> x
                .getHigh()
                .getUnit(), x -> x
                .getLow()
                .getCode(), x -> x
                .getHigh()
                .getCode())
            .containsOnly("mmol/L");
    }
}
