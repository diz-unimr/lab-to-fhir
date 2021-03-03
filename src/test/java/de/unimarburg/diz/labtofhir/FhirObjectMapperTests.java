package de.unimarburg.diz.labtofhir;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.unimarburg.diz.labtofhir.configuration.JacksonConfiguration;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ContextConfiguration(classes = {JacksonConfiguration.class})
public class FhirObjectMapperTests {

    @Value("classpath:test-input.json")
    Resource input;
    @Autowired
    private ObjectMapper mapper;

    @Test
    public void inputMapsToModel() throws IOException {
        var report = mapper.readValue(input.getInputStream(), LaboratoryReport.class);

        assertThat(report).extracting(LaboratoryReport::getId, LaboratoryReport::getResource,
            LaboratoryReport::getInserted, LaboratoryReport::getModified)
            .doesNotContainNull();
    }

}
