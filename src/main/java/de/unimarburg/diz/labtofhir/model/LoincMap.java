package de.unimarburg.diz.labtofhir.model;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipFile;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

public class LoincMap {

    private final Map<String, Set<LoincMapEntry>> internalMap = new HashMap<>();
    private final Logger log = LoggerFactory.getLogger(LoincMap.class);
    private CsvPackageMetadata metadata;

    private LoincMap parsePackage(Resource pkg, char delimiter)
        throws IOException {

        var zipFile = new ZipFile(pkg.getFile());

        // metadata
        var pkgMetadata = zipFile.getEntry("datapackage.json");
        if (pkgMetadata == null) {
            log.error("Error locating metadata file in data package: {}",
                zipFile.getName());
            // TODO
            throw new IllegalArgumentException();
        }
        // parse data
        var inputStream = zipFile.getInputStream(pkgMetadata);
        metadata = parseMetadata(inputStream);
        inputStream.close();

        // mapping file
        var mappingFileName = metadata
            .getResources()
            .stream()
            .findAny()
            .orElseThrow()
            .getPath();
        var pkgResource = zipFile.getEntry(mappingFileName);
        if (pkgResource == null) {
            log.error("Error locating LOINC mapping file in data package: {}",
                zipFile.getName());
            // TODO
            throw new IllegalArgumentException();
        }
        // parse data
        inputStream = zipFile.getInputStream(pkgResource);
        var map = buildLoincMap(inputStream, delimiter);
        inputStream.close();
        return map;


    }

    private LoincMap buildLoincMap(InputStream inputStream, char delimiter) {
        var entries = loadItems(LoincMapEntry.class, inputStream, delimiter);
        entries
            .stream()
            .filter(
                i -> !StringUtils.isBlank(i.getLoinc()) && !StringUtils.isBlank(
                    i.getUcum()))
            .forEach(i -> put(i.getSwl(), i));
        log.info(
            "Building LOINC map, using version: {}, date: {}, git revision: {}."
                + " File checksum (SHA-256): {}.", metadata.getVersion(),
            metadata.getCreated(), metadata.getGitCommit(),
            metadata.getChecksum());
        log.info("LOINC map initialized with {} entries.", internalMap.size());
        return this;

    }

    public CsvPackageMetadata parseMetadata(InputStream mappingFile)
        throws IOException {

        return new ObjectMapper()
            .findAndRegisterModules()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .readerFor(CsvPackageMetadata.class)
            .readValue(mappingFile, CsvPackageMetadata.class);
    }

    public void put(String code, LoincMapEntry entry) {
        // 1. source: entries are ordered with null values being last
        // 2. group code: entries are ordered with group codes being last
        var entries = internalMap.computeIfAbsent(code, s -> new TreeSet<>(
            comparing(LoincMapEntry::getMeta,
                nullsFirst(naturalOrder())).thenComparing(
                LoincMapEntry::getGroupCode, Boolean::compare)));
        entries.add(entry);
    }

    public LoincMapEntry get(String code, String source) {
        if (!internalMap.containsKey(code)) {
            return null;
        }
        var entries = internalMap.get(code);

        return entries
            .stream()
            .filter(e -> StringUtils.equals(e.getMeta(), source))
            .findFirst()
            // fallback to null source
            .orElseGet(() -> entries
                .stream()
                .filter(e -> StringUtils.equals(e.getMeta(), null))
                .findFirst()
                .orElse(null));

    }

    public int size() {
        return internalMap.size();
    }

    public CsvPackageMetadata metadata() {
        return metadata;
    }

    private <T> List<T> loadItems(Class<T> type, InputStream inputStream,
        char delimiter) {
        try {
            var bootstrapSchema = CsvSchema
                .emptySchema()
                .withHeader()
                .withComments()
                .withColumnSeparator(delimiter);
            var mapper = new CsvMapper().disable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            MappingIterator<T> readValues = mapper
                .readerFor(type)
                .with(bootstrapSchema)
                .readValues(inputStream);
            return readValues.readAll();
        } catch (IOException e) {
            log.error(
                "Error occurred while loading object list from input stream.",
                e);
            return Collections.emptyList();
        }
    }

    public LoincMap with(Resource pkgResource, char delimiter)
        throws IOException {

        if (pkgResource.exists() && pkgResource.isFile()) {
            return parsePackage(pkgResource, delimiter);
        }
        throw new IllegalArgumentException("Mapping resource must be a file.");
    }

}
