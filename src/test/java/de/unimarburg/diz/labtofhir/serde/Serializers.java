package de.unimarburg.diz.labtofhir.serde;

import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.LoincMapEntry;
import org.springframework.kafka.support.serializer.JsonSerializer;

public class Serializers {

    public static class LaboratoryReportSerializer extends JsonSerializer<LaboratoryReport> {

    }

    public static class LoincMapEntrySerializer extends JsonSerializer<LoincMapEntry> {

    }

}
