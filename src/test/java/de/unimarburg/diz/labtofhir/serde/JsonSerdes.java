package de.unimarburg.diz.labtofhir.serde;

import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import org.springframework.kafka.support.serializer.JsonSerde;

public class JsonSerdes {

    public static JsonSerde<LaboratoryReport> laboratoryReport() {
        return new JsonSerde<>(LaboratoryReport.class);
    }

}
