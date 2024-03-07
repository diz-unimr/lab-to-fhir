package de.unimarburg.diz.labtofhir.mapper;

import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.metric.TagCounter;
import de.unimarburg.diz.labtofhir.model.LoincMap;
import de.unimarburg.diz.labtofhir.model.LoincMapEntry;
import io.micrometer.core.instrument.Metrics;
import java.io.IOException;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.Resource;

//@Service
public class LoincMapper {

    private final FhirProperties fhirProperties;
    private final Resource mappingPackage;
    private static final Logger LOG = LoggerFactory.getLogger(
        LoincMapper.class);
    private final TagCounter unmappedCodes;

    public LoincMap getMap() {
        return loincMap;
    }

    private LoincMap loincMap;

    @Autowired
    public LoincMapper(FhirProperties fhirProperties,
        @Qualifier("mappingPackage") Resource mappingPackage) {
        this.fhirProperties = fhirProperties;
        this.unmappedCodes = new TagCounter("loinc_unmapped",
            "Number of unmapped resources by code", "swl_code",
            Metrics.globalRegistry);

        this.mappingPackage = mappingPackage;
    }

    public LoincMapper(FhirProperties fhirProperties, LoincMap loincMap) {
        this.fhirProperties = fhirProperties;
        this.unmappedCodes = new TagCounter("loinc_unmapped",
            "Number of unmapped resources by code", "swl_code",
            Metrics.globalRegistry);

        this.loincMap = loincMap;
        this.mappingPackage = null;
    }

    public static LoincMap getSwlLoincMapping(Resource mappingPackage)
        throws IOException {
        return new LoincMap().with(mappingPackage, ',');
    }

    public LoincMapper initialize() throws Exception {
        initializeMap();
        return this;
    }

    //    @PostConstruct
    private void initializeMap() throws Exception {
        if (this.mappingPackage != null) {
            this.loincMap = getSwlLoincMapping(mappingPackage);
        }
    }

    public Observation map(Observation obs, String metaCode) {
        // LOINC mapping supported for Quantity type, only
        if (!obs.hasValueQuantity()) {
            return obs;
        }

        var swlCode = obs
            .getCode()
            .getCoding()
            .stream()
            .filter(c -> c
                .getSystem()
                .equals(fhirProperties
                    .getSystems()
                    .getLaboratorySystem()))
            .map(Coding::getCode)
            .findAny();
        if (swlCode.isEmpty()) {
            return obs;
        }
        var mapping = loincMap.get(swlCode.get(), metaCode);
        if (mapping == null) {
            unmappedCodes.increment(swlCode.get());
            return obs;
        }

        return map(obs, mapping);
    }

    private Observation map(Observation obs, LoincMapEntry entry) {

        LOG.debug("Found mapping for code: {} with LOINC: {}", entry.getSwl(),
            entry.getLoinc());

        // add loinc coding
        var loincCoding = new Coding()
            .setSystem("http://loinc.org")
            .setCode(entry.getLoinc());
        obs
            .getCode()
            .getCoding()
            .add(0, loincCoding);

        // map ucum in value and referenceRange(s)
        obs
            .getValueQuantity()
            .setUnit(entry.getUcum())
            .setCode(entry.getUcum())
            .setSystem("http://unitsofmeasure.org");
        obs
            .getReferenceRange()
            .forEach(quantity -> {
                if (quantity.hasLow()) {
                    quantity
                        .getLow()
                        .setUnit(entry.getUcum())
                        .setCode(entry.getUcum())
                        .setSystem("http://unitsofmeasure.org");
                }
                if (quantity.hasHigh()) {
                    quantity
                        .getHigh()
                        .setUnit(entry.getUcum())
                        .setCode(entry.getUcum())
                        .setSystem("http://unitsofmeasure.org");
                }
            });

        return obs;
    }
}
