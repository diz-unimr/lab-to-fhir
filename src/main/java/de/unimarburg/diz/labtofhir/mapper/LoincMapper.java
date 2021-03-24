package de.unimarburg.diz.labtofhir.mapper;

import de.unimarburg.diz.labtofhir.model.LoincMap;
import javax.annotation.PostConstruct;
import org.hl7.fhir.r4.model.Observation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoincMapper {

    private final static Logger log = LoggerFactory.getLogger(LoincMapper.class);
    @Value("${mapping.loinc.file}")
    private String mappingFile;
    private LoincMap loincMap;

    private LoincMap getSwlLoincMapping(String mappingFile) {
        return new LoincMap().with(mappingFile, '\t');
    }

    public Observation mapCodeAndQuantity(Observation obs, String metaCode) {
        var coding = obs.getCode().getCoding().get(0);
        var entry = loincMap.get(coding.getCode(), metaCode);
        if (entry == null) {
            throw new IllegalArgumentException(String.format(
                "LOINC mapping lookup failed. No values found for code: %s and meta code: %s",
                coding.getCode(), metaCode));
        }

        // map code
        coding.setCode(entry.getLoinc())
            .setSystem("http://loinc.org");

        if (obs.hasValueQuantity()) {
            // map ucum
            var valueQuantity = obs.getValueQuantity();

            valueQuantity.setCode(entry.getUcum());
            valueQuantity.setSystem("http://unitsofmeasure.org");
        }
        return obs;
    }

    @PostConstruct
    private void initializeMap() {
        this.loincMap = getSwlLoincMapping(mappingFile);
    }

    public boolean hasMappings() {
        return loincMap.size() > 0;
    }
}
