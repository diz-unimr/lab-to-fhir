package de.unimarburg.diz.labtofhir.processor;

import de.unimarburg.diz.labtofhir.UpdateCompleted;
import de.unimarburg.diz.labtofhir.mapper.AimLabMapper;
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

    private static final Logger LOG =
        LoggerFactory.getLogger(LabUpdateProcessor.class);
    private final AimLabMapper reportMapper;
    private final ConcurrentHashMap<Integer, OffsetTarget> offsetState;
    private final ApplicationEventPublisher eventPublisher;
    private MappingUpdate mappingVersion;


    public LabUpdateProcessor(AimLabMapper reportMapper,
        @Nullable MappingInfo mappingInfo, LabOffsets offsets,
        ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.reportMapper = reportMapper;
        if (mappingInfo != null) {
            this.mappingVersion = mappingInfo.update();
        }

        this.offsetState = new ConcurrentHashMap<>(
            offsets.processOffsets().entrySet().stream().collect(
                Collectors.toMap(Entry::getKey,
                    e -> new OffsetTarget(e.getValue().offset(), false))));
    }

    @SuppressWarnings("checkstyle:LineLength")
    @Bean
    @ConditionalOnBean(MappingInfo.class)
    public Function<KStream<String, LaboratoryReport>, KStream<String, Bundle>> update() {

        return report -> report.process(
                () -> new ContextualProcessor<String, LaboratoryReport, String, Bundle>() {

                    @Override
                    public void process(Record<String, LaboratoryReport> record) {
                        var currentPartition =
                            context().recordMetadata().orElseThrow().partition();
                        var currentOffset =
                            context().recordMetadata().orElseThrow().offset();

                        // check partitions and offsets
                        var partitionState = offsetState.get(currentPartition);
                        if (currentOffset >= partitionState.offset()
                            && checkCompleted(offsetState, currentPartition)) {

                            // all done
                            return;
                        }

                        // filter for update codes
                        if (record.value().getObservations().stream().anyMatch(
                            o -> o.getCode().getCoding().stream().anyMatch(
                                c -> mappingVersion.getUpdates()
                                    .contains(c.getCode())))) {

                            // map
                            var bundle = reportMapper.apply(record.value());
                            context().forward(record.withValue(bundle));
                        }

                        // check completed for current offset +1
                        if (currentOffset + 1 >= partitionState.offset()) {
                            checkCompleted(offsetState, currentPartition);
                        }
                    }
                })
            // filter
            .filter((k, v) -> v != null);
    }

    private boolean checkCompleted(
        ConcurrentHashMap<Integer, OffsetTarget> offsetState,
        int currentPartition) {
        var partitionState = offsetState.get(currentPartition);
        if (partitionState.done()) {
            // already done; just return
            return true;
        } else {
            this.offsetState.computeIfPresent(currentPartition,
                (partition, target) -> new OffsetTarget(target.offset(), true));

        }

        // all done
        if (this.offsetState.values().stream().allMatch(s -> s.done)) {

            LOG.info("Update done with offset state {}", offsetState);
            // send completed event
            sendCompleted();
            return true;
        }

        return false;
    }

    private void sendCompleted() {
        eventPublisher.publishEvent(new UpdateCompleted(this));
    }

    private record OffsetTarget(long offset, boolean done) {

    }
}
