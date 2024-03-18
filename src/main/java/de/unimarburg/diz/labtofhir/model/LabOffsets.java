package de.unimarburg.diz.labtofhir.model;

import java.util.Map;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;

public record LabOffsets(Map<Integer, OffsetAndMetadata> processOffsets,
                         Map<Integer, OffsetAndMetadata> updateOffsets) {

}
