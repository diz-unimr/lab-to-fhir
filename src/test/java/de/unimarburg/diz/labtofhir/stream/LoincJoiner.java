package de.unimarburg.diz.labtofhir.stream;

import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.stereotype.Service;

@Service
public class LoincJoiner implements ValueJoiner<Bundle, LoincMap, Bundle> {

    private final FhirProperties fhirProperties;

    public LoincJoiner(FhirProperties fhirProperties) {
        this.fhirProperties = fhirProperties;
    }

    @Override
    public Bundle apply(Bundle bundle, LoincMap loincMap) {

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
            .findAny()
            .orElse(null);
        // get mapping
        var entry = loincMap.entry(meta);

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
