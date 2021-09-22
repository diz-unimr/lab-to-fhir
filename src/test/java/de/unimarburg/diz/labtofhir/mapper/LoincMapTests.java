package de.unimarburg.diz.labtofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.model.LoincMap;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

public class LoincMapTests {

    private final LoincMap loincMap = new LoincMap();

    @Test
    public void loincMapReadsDataPackage() throws IOException {
        // arrange
        var resource = new ClassPathResource("mapping-swl-loinc.zip");

        // act
        loincMap.with(resource, ',');

        // assert
        assertThat(loincMap.metadata()).hasNoNullFieldsOrProperties();
        assertThat(loincMap.size()).isEqualTo(671);
    }
}