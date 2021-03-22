package de.unimarburg.diz.labtofhir.mapper;

import de.unimarburg.diz.labtofhir.model.LoincMap;
import java.util.List;
import javax.annotation.PostConstruct;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LoincMapper {

    @Value("${mapping.loinc.file}")
    private String mappingFile;
    private LoincMap loincMap;

    private LoincMap getSwlLoincMapping(String mappingFile) {
        return new LoincMap().with(mappingFile, '\t');
    }

    private String mapLoincCode(String code, String metaCode) {
        var entry = loincMap.get(code, metaCode);
        return entry.getLoinc();
    }

    public List<Coding> mapCoding(Coding coding, String metaCode) {
        // assume swl code
        var swlCode = coding.getCode();

        return List.of(new Coding().setSystem("http://loinc.org")
            .setCode(mapLoincCode(swlCode, metaCode)));
    }


    @PostConstruct
    private void initializeMap() {
        this.loincMap = getSwlLoincMapping(mappingFile);
    }

    public boolean hasMappings() {
        return loincMap.size() > 0;
    }
}
