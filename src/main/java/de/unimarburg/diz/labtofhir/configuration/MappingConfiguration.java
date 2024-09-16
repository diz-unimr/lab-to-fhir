package de.unimarburg.diz.labtofhir.configuration;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import de.unimarburg.diz.labtofhir.mapper.DateFilter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Pattern;
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

    @Bean("aimFilter")
    public DateFilter aimFilter(
        @Value("${mapping.aim.filter:#{null}}") String filterExpression) {
        if (StringUtils.isBlank(filterExpression)) {
            return null;
        }
        return createDateFilter(filterExpression);
    }

    @Bean("hl7Filter")
    public DateFilter hl7Filter(
        @Value("${mapping.hl7.filter:#{null}}") String filterExpression) {
        if (StringUtils.isBlank(filterExpression)) {
            return null;
        }
        return createDateFilter(filterExpression);
    }

    DateFilter createDateFilter(String filterExpression) {
        var pattern =
            Pattern.compile("^(<|>|<=|>=|=)(\\d{4}-\\d{2}-\\d{2})$");

        var matcher = pattern.matcher(filterExpression);

        if (matcher.find() && matcher.groupCount() == 2) {
            var comp = matcher.group(1);
            var date = matcher.group(2);

            return new DateFilter(LocalDate.parse(date), comp);
        }

        throw new IllegalStateException(
            "Invalid filter expression: " + filterExpression);

    }
}
