package de.unimarburg.diz.labtofhir.stream;

import ca.uhn.fhir.context.FhirContext;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.LoincMapEntry;
import de.unimarburg.diz.labtofhir.serde.JsonSerdes;
import de.unimarburg.diz.labtofhir.serializer.FhirSerde;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;

public class LabLoincStream {

    public static StreamsBuilder createStream(StreamsBuilder builder) {
        final KStream<Integer, LaboratoryReport> labTable = builder.stream("lab",
            Consumed.with(Serdes.Integer(), JsonSerdes.LaboratoryReport()));
        final KTable<String, LoincMapEntry> loincTable = builder.table("loinc",
            Consumed.with(Serdes.String(), JsonSerdes.LoincMapEntry()));

        var fhirProperties = new FhirProperties();
        fhirProperties.setGenerateNarrative(false);

        labTable
            .mapValues(new LabReportMapper(FhirContext.forR4(), fhirProperties))
            .filter((k, v) -> v != null)
            .map((k, v) -> new KeyValue<>(v
                .getValue()
                .getId(), v.getValue()))
            //            .toTable(Named.as("aim-lab-mapped"))
            // TODO remap key?
            .flatMap(LabLoincStream::splitEntries)
            .toTable(Named.as("entries"),
                Materialized.with(Serdes.String(), new FhirSerde<>(Bundle.class)))
            .leftJoin(loincTable, LabLoincStream.swlCodeExtractor(), new LoincJoiner())
            .toStream()

            .to("lab-mapped", Produced.with(Serdes.String(), new FhirSerde<>(Bundle.class)));

        return builder;
    }

    private static List<KeyValue<String, Bundle>> splitEntries(String key, Bundle bundle) {
        return bundle
            .getEntry()
            .stream()
            .map(e -> new KeyValue<>(e
                .getResource()
                .getResourceType() + e
                .getResource()
                .getId(), new Bundle().setEntry(List.of(e))))
            .toList();
    }

    private static Function<Bundle, String> swlCodeExtractor() {

        return bundle -> {
            var resource = bundle
                .getEntryFirstRep()
                .getResource();
            if (resource instanceof Observation obs) {
                return obs
                    .getCode()
                    .getCoding()
                    .stream()
                    // .filter(x -> "https://fhir.diz.uni-marburg.de/CodeSystem/swisslab-code".equals(
                    // x.getSystem()))
                    .findAny()
                    .map(Coding::getCode)
                    .orElse(UUID
                        .randomUUID()
                        .toString());
            }
            return UUID
                .randomUUID()
                .toString();
        };
    }

}
