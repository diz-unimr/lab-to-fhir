package de.unimarburg.diz.labtofhir.serde;

import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.LoincMapEntry;
import org.springframework.kafka.support.serializer.JsonSerde;

public class JsonSerdes {

    public static JsonSerde<LaboratoryReport> LaboratoryReport() {
        return new JsonSerde<>(LaboratoryReport.class);
    }

    public static JsonSerde<LoincMapEntry> LoincMapEntry() {
        return new JsonSerde<>(LoincMapEntry.class);
    }

}
