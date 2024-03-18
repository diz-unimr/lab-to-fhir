package de.unimarburg.diz.labtofhir.serde;

import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.LoincMapTests;
import org.springframework.kafka.support.serializer.JsonSerde;

public class JsonSerdes {

    public static JsonSerde<LaboratoryReport> laboratoryReport() {
        return new JsonSerde<>(LaboratoryReport.class);
    }

    public static JsonSerde<LoincMapTests> loincMapEntry() {
        return new JsonSerde<>(LoincMapTests.class);
    }

}
