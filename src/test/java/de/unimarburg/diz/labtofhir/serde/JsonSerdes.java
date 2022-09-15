package de.unimarburg.diz.labtofhir.serde;

import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.stream.LoincMap;
import org.springframework.kafka.support.serializer.JsonSerde;

public class JsonSerdes {

    public static JsonSerde<LaboratoryReport> LaboratoryReport() {
        return new JsonSerde<>(LaboratoryReport.class);
    }

    public static JsonSerde<LoincMap> LoincMapEntry() {
        return new JsonSerde<>(LoincMap.class);
    }

}
