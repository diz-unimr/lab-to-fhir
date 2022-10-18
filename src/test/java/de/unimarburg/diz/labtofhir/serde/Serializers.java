package de.unimarburg.diz.labtofhir.serde;

import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.LoincMap;
import org.springframework.kafka.support.serializer.JsonSerializer;

public class Serializers {

    public static class LaboratoryReportSerializer extends JsonSerializer<LaboratoryReport> {

    }

    public static class LoincMapSerializer extends JsonSerializer<LoincMap> {

    }

}
