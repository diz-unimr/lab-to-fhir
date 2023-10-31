package de.unimarburg.diz.labtofhir;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.kafka.support.JacksonUtils;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
public class FhirObjectMapperTests {

    private final ObjectMapper mapper = JacksonUtils.enhancedObjectMapper();
    @Value("classpath:reports/test-input.json")
    private Resource input;

    @Test
    public void inputMapsToModel() throws IOException {
        var inputString = new String(Files.readAllBytes(input
            .getFile()
            .toPath()));
        var report = mapper.readValue(inputString, LaboratoryReport.class);

        assertThat(report)
            .extracting(LaboratoryReport::getId, LaboratoryReport::getResource,
                LaboratoryReport::getInserted, LaboratoryReport::getModified)
            .doesNotContainNull();
    }
}
