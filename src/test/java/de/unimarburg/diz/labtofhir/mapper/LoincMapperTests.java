package de.unimarburg.diz.labtofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;


@SpringBootTest
@ContextConfiguration(classes = {LoincMapper.class})
@TestPropertySource(
    properties = {
        "mapping.loinc.file=mapping_swl_loinc-v1.2.csv",
    })
public class LoincMapperTests {

    @Autowired
    private LoincMapper loincMapper;

    @Test
    public void loincMapIsInitialized() {
        assertThat(loincMapper.hasMappings()).isTrue();
    }

}
