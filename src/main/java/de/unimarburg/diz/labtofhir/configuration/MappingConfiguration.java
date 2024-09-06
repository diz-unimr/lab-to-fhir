package de.unimarburg.diz.labtofhir.configuration;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class MappingConfiguration {

    @Bean("categoryMap")
    public Map<String, String> categoryMappingFile(
        @Value("classpath:Mappingkatalog.csv") Resource categoryMap)
        throws IOException {

        var bootstrapSchema = CsvSchema
            .emptySchema()
            .withHeader()
            .withComments()
            .withColumnSeparator(';');
        var mapper = new CsvMapper();
        MappingIterator<Map<String, String>> readValues = mapper
            .readerForMapOf(String.class)
            .with(bootstrapSchema)
            .withFeatures(CsvParser.Feature.EMPTY_STRING_AS_NULL)
            .readValues(categoryMap.getInputStream());
        return readValues.readAll().stream()
            .collect(Collectors.toMap(k -> k.get("ANALYTCODE"),
                v -> v.get("KATEGORIE"),
                (code1, code2) -> {
                    // duplicate keys, take first for compatability
                    return code1;
                }));
    }
}
