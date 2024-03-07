package de.unimarburg.diz.labtofhir.processor;

import de.unimarburg.diz.labtofhir.LabRunner;
import de.unimarburg.diz.labtofhir.UpdateCompleted;
import de.unimarburg.diz.labtofhir.mapper.MiiLabReportMapper;
import de.unimarburg.diz.labtofhir.model.LabOffsets;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.MappingInfo;
import de.unimarburg.diz.labtofhir.model.MappingUpdate;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.processor.api.ContextualProcessor;
import org.apache.kafka.streams.processor.api.Record;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
public class LabUpdateProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(
        LabUpdateProcessor.class);
    private MappingUpdate mappingVersion;
    private final MiiLabReportMapper reportMapper;
    private final ConcurrentHashMap<Integer, OffsetTarget> offsetState;
    private final ApplicationEventPublisher eventPublisher;


    public LabUpdateProcessor(LabRunner labRunner,
        MiiLabReportMapper reportMapper, @Nullable MappingInfo mappingInfo,
        LabOffsets offsets, ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.reportMapper = reportMapper;
        if (mappingInfo != null) {
            this.mappingVersion = mappingInfo.update();
        }

        this.offsetState = new ConcurrentHashMap<>(offsets
            .processOffsets()
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Entry::getKey, e -> new OffsetTarget(e
                .getValue()
                .offset(), false))));
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Bean
    @ConditionalOnBean(MappingInfo.class)
    public Function<KStream<String, LaboratoryReport>, KStream<String, Bundle>> update() {

        return report -> report.process(
                () -> new ContextualProcessor<String, LaboratoryReport, String, Bundle>() {

                    @Override
                    public void process(Record<String, LaboratoryReport> record) {
                        var currentPartition = context()
                            .recordMetadata()
                            .orElseThrow()
                            .partition();
                        var currentOffset = context()
                            .recordMetadata()
                            .orElseThrow()
                            .offset();

                        // check partitions and offsets
                        var partitionState = offsetState.get(currentPartition);
                        if (partitionState.offset() <= currentOffset + 1) {
                            if (!partitionState.done()) {

                                offsetState.computeIfPresent(currentPartition,
                                    (partition, target) -> new OffsetTarget(
                                        target.offset(), true));
                            }

                            // all done
                            if (offsetState
                                .values()
                                .stream()
                                .allMatch(s -> s.done)) {

                                // send completed event
                                eventPublisher.publishEvent(
                                    new UpdateCompleted(this));
                            }

                            return;
                        }

                        // filter for update codes
                        if (record
                            .value()
                            .getObservations()
                            .stream()
                            .anyMatch(o -> o
                                .getCode()
                                .getCoding()
                                .stream()
                                .anyMatch(c -> mappingVersion
                                    .getUpdates()
                                    .contains(c.getCode())))) {

                            // map
                            var bundle = reportMapper.apply(record.value());

                            context().forward(record.withValue(bundle));
                        }
                    }
                })
            // filter
            .filter((k, v) -> v != null);
    }

    private record OffsetTarget(long offset, boolean done) {

    }
}
