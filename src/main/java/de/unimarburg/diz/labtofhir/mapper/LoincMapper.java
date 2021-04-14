package de.unimarburg.diz.labtofhir.mapper;

import de.unimarburg.diz.labtofhir.model.LoincMap;
import de.unimarburg.diz.labtofhir.model.LoincMappingResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import java.util.HashMap;
import javax.annotation.PostConstruct;
import org.hl7.fhir.r4.model.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoincMapper {

    private static final String MAPPING_ERROR_METRIC_NAME =
        "labtofhir.loinc.mapping.errors.total";
    private static final HashMap<String, Counter> metricsLookup = new HashMap<>();

    private final static Logger log = LoggerFactory.getLogger(LoincMapper.class);
    @Value("${mapping.loinc.file}")
    private String mappingFile;
    private LoincMap loincMap;

    private LoincMap getSwlLoincMapping(String mappingFile) {
        return new LoincMap().with(mappingFile, '\t');
    }

    public LoincMappingResult mapCodeAndQuantity(Observation obs,
        String metaCode) {

        if (obs.hasValueQuantity()) {
            // get code and mapping
            var coding = obs.getCode().getCoding().get(0);
            var entry = loincMap.get(coding.getCode(), metaCode);
            if (entry == null) {
                // TODO metric
                log.warn(
                    "LOINC mapping lookup failed. No values found for code: {} and meta code: {}",
                    coding.getCode(), metaCode);

                var codesDesc = coding.getCode();
                if (metaCode != null) {
                    codesDesc += "#" + metaCode;
                }

                metricsLookup.putIfAbsent(
                    codesDesc,
                    Metrics.globalRegistry
                        .counter(MAPPING_ERROR_METRIC_NAME, "code", codesDesc));
                metricsLookup.get(codesDesc).increment();

                return LoincMappingResult.MISSING_CODE_MAPPING;
            }

            // map code
            coding.setCode(entry.getLoinc())
                .setSystem("http://loinc.org");

            // map ucum
            obs.getValueQuantity().setUnit(entry.getUcum())
                .setCode(entry.getUcum()).setSystem("http://unitsofmeasure.org");

            return LoincMappingResult.SUCCESS;
        }

        // text value
        // TODO map to CodeableConcept (i.e. Snomed code)
        return LoincMappingResult.MISSING_QUANTITY;
    }

    @PostConstruct
    private void initializeMap() {
        this.loincMap = getSwlLoincMapping(mappingFile);
    }

    public boolean hasMappings() {
        return loincMap.size() > 0;
    }
}
