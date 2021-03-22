package de.unimarburg.diz.labtofhir.model;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

public class LoincMap {

    private final Map<String, Set<LoincMapEntry>> internalMap = new HashMap<>();
    private final Logger log = LoggerFactory.getLogger(LoincMap.class);


    public LoincMap with(String mappingFile, char delimiter) {
        var entries = loadObjectList(LoincMapEntry.class, mappingFile, delimiter);
        entries.stream().filter(i -> i.isValidated() && !StringUtils.isBlank(i.getLoinc())
            && !StringUtils.isBlank(i.getUcum())).forEach(i -> put(i.getSwl(), i));

        return this;
    }

    public void put(String code, LoincMapEntry entry) {
        // 1. source: entries are ordered with null values being last
        // 2. group code: entries are ordered with group codes being last
        var entries = internalMap.computeIfAbsent(code, s -> new TreeSet<>(
            comparing(LoincMapEntry::getSource, nullsLast(naturalOrder())).thenComparing(
                LoincMapEntry::getGroupCode, Boolean::compare)));
        entries.add(entry);
    }

    public LoincMapEntry get(String code, String source) {
        if (!internalMap.containsKey(code)) {
            return null;
        }
        var entries = internalMap.get(code);

        return entries.stream()
            .filter(e -> StringUtils.equals(e.getSource(), source))
            .findFirst()
            .orElseGet(() -> entries.stream()
                .findFirst()
                .orElse(null));

    }

    public int size() {
        return internalMap.size();
    }

    private <T> List<T> loadObjectList(Class<T> type, String fileName, char delimiter) {
        try {
            var bootstrapSchema = CsvSchema.emptySchema().withHeader()
                .withColumnSeparator(delimiter);
            var mapper = new CsvMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            var file = new ClassPathResource(fileName).getFile();
            MappingIterator<T> readValues =
                mapper.readerFor(type).with(bootstrapSchema).readValues(file);
            return readValues.readAll();
        } catch (Exception e) {
            log.error("Error occurred while loading object list from file " + fileName, e);
            return Collections.emptyList();
        }
    }
}
