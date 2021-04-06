package de.unimarburg.diz.labtofhir;

import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import org.springframework.kafka.support.serializer.JsonDeserializer;

public class LaboratoryReportDeserializer extends JsonDeserializer<LaboratoryReport> {

}
