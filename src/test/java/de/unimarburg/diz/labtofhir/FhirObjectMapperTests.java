package de.unimarburg.diz.labtofhir;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.file.Files;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(SpringExtension.class)
public class FhirObjectMapperTests {
    private final ObjectMapper mapper = JacksonUtils.enhancedObjectMapper();
    @Value("classpath:reports/test-input.json")
    Resource input;

    @Test
    public void inputMapsToModel() throws IOException {
        var inputString = new String(Files.readAllBytes(input.getFile().toPath()));
        var report = mapper.readValue(inputString, LaboratoryReport.class);

        assertThat(report).extracting(LaboratoryReport::getId, LaboratoryReport::getResource,
                LaboratoryReport::getInserted, LaboratoryReport::getModified)
            .doesNotContainNull();
    }
}
