package de.unimarburg.diz.labtofhir.stream;

import de.unimarburg.diz.labtofhir.model.LoincMapEntry;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;

public class LoincJoiner implements ValueJoiner<Bundle, LoincMapEntry, Bundle> {

    @Override
    public Bundle apply(Bundle bundle, LoincMapEntry loincMapEntry) {

        bundle
            .getEntry()
            .stream()
            .map(BundleEntryComponent::getResource)
            .filter(r -> {
                if (r instanceof Observation obs) {
                    return obs
                        .getCode()
                        .getCoding()
                        .stream()
                        .anyMatch(c -> loincMapEntry
                            .getSwl()
                            .equals(c.getCode()));
                }
                return false;
            })
            .map(Observation.class::cast)
            .forEach(o -> o
                .getCode()
                .addCoding(new Coding()
                    .setSystem("loinc")
                    .setCode(loincMapEntry.getLoinc())));
        return bundle;
    }
}
