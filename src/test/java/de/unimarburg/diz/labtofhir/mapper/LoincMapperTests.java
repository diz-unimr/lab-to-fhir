package de.unimarburg.diz.labtofhir.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import de.unimarburg.diz.labtofhir.configuration.MappingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;


@SpringBootTest
@ContextConfiguration(classes = {LoincMapper.class, MappingConfiguration.class})
public class LoincMapperTests {

    @Autowired
    private LoincMapper loincMapper;

    @Test
    public void loincMapIsInitialized() {

        assertThat(loincMapper.hasMappings()).isTrue();
    }
}
