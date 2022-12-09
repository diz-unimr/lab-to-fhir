package de.unimarburg.diz.labtofhir.mapper;

import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.LoincMap;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoincMapper implements ValueJoiner<Bundle, LoincMap, Bundle> {

    private final FhirProperties fhirProperties;
    private final static Logger log = LoggerFactory.getLogger(LoincMapper.class);

    public LoincMapper(FhirProperties fhirProperties) {
        this.fhirProperties = fhirProperties;
    }

    @Override
    public Bundle apply(Bundle bundle, LoincMap loincMap) {
        if (loincMap == null) {
            return bundle;
        }

        bundle
            .getEntry()
            .stream()
            .map(BundleEntryComponent::getResource)
            .filter(Observation.class::isInstance)
            .map(Observation.class::cast)
            .filter(o -> o
                .getCode()
                .hasCoding(fhirProperties
                    .getSystems()
                    .getLaboratorySystem(), loincMap.getSwl()))
            .forEach(o -> mapCodeAndQuantity(o, loincMap));
        return bundle;
    }

    private void mapCodeAndQuantity(Observation obs, LoincMap loincMap) {

        if (!obs
            .getCode()
            .hasCoding(fhirProperties
                .getSystems()
                .getLaboratorySystem(), loincMap.getSwl()) || !obs.hasValueQuantity()) {
            return;
        }
        // check if meta code exists
        var meta = obs
            .getCode()
            .getCoding()
            .stream()
            .filter(c -> StringUtils.equals(c.getSystem(), fhirProperties
                .getSystems()
                .getLabReportMetaSystem()))
            .map(Coding::getCode)
            .filter(Objects::nonNull)
            .findAny()
            .orElse(null);
        if (meta != null) {
            log.debug("Meta code '{}' found for {}", meta, loincMap.getSwl());
        }

        // get mapping
        var entry = loincMap.entry(meta);
        log.debug("Found mapping for code: {} with LOINC: {}", loincMap.getSwl(), entry.getLoinc());

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
    }
}
