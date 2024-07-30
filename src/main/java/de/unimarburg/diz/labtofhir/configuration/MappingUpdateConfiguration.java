package de.unimarburg.diz.labtofhir.configuration;

import de.unimarburg.diz.labtofhir.mapper.LoincMapper;
import de.unimarburg.diz.labtofhir.model.LabOffsets;
import de.unimarburg.diz.labtofhir.model.MappingInfo;
import de.unimarburg.diz.labtofhir.model.MappingUpdate;
import de.unimarburg.diz.labtofhir.util.ResourceHelper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MappingUpdateConfiguration {

    private static final Logger LOG =
        LoggerFactory.getLogger(MappingUpdateConfiguration.class);
    private static final long MAX_CONSUMER_POLL_DURATION_SECONDS = 10L;

    @Bean("mappingInfo")
    public MappingInfo buildMappingUpdate(LoincMapper loincMapper,
        MappingProperties mappingProperties, LabOffsets labOffsets,
        Consumer<String, MappingUpdate> consumer,
        Producer<String, MappingUpdate> producer)
        throws ExecutionException, InterruptedException, IOException {
        // check versions
        var configuredVersion = loincMapper.getMap().getMetadata().getVersion();

        // 1. consume latest from (mapping) update topic
        var lastUpdate = getLastMappingUpdate(consumer);
        if (lastUpdate == null) {
            // job runs the first time: save current state
            lastUpdate = new MappingUpdate(configuredVersion, null, List.of());
            try {
                saveMappingUpdate(producer, lastUpdate);
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Failed to save mapping update to topic.");
                throw e;
            }

        }

        // 2. check if already on latest
        if (Objects.equals(configuredVersion, lastUpdate.getVersion())) {
            LOG.info(
                "Configured mapping version ({}) matches last version ({}). "
                    + "No update necesssary", configuredVersion,
                lastUpdate.getVersion());

            if (!labOffsets.updateOffsets().isEmpty()) {
                // update in progress, continue
                return new MappingInfo(lastUpdate, true);
            }

            return null;
        }

        // 3. calculate diff of mapping versions and save new update
        // get last version's mapping
        var lastMap = LoincMapper.getSwlLoincMapping(
            ResourceHelper.getMappingFile(lastUpdate.getVersion(),
                mappingProperties.getLoinc().getCredentials().getUser(),
                mappingProperties.getLoinc().getCredentials().getPassword(),
                mappingProperties.getLoinc().getProxy(),
                mappingProperties.getLoinc().getLocal()));
        // ceate diff
        var updates = loincMapper.getMap().diff(lastMap);
        var update =
            new MappingUpdate(configuredVersion, lastUpdate.getVersion(),
                updates);

        // save new mapping update
        saveMappingUpdate(producer, update);

        return new MappingInfo(update, false);
    }

    @SuppressWarnings("checkstyle:LineLength")
    private void saveMappingUpdate(Producer<String, MappingUpdate> producer,
        MappingUpdate mappingUpdate)
        throws ExecutionException, InterruptedException {

        producer.send(
                new ProducerRecord<>("mapping", "aim-lab-update", mappingUpdate))
            .get();
    }

    private MappingUpdate getLastMappingUpdate(
        Consumer<String, MappingUpdate> consumer) {
        var topic = "mapping";

        var partition = new TopicPartition(topic, 0);
        var partitions = List.of(partition);

        try (consumer) {
            consumer.assign(partitions);
            consumer.seekToEnd(partitions);
            var position = consumer.position(partition);
            if (position == 0) {
                return null;
            }

            consumer.seek(partition, position - 1);

            var record = consumer
                .poll(Duration.ofSeconds(MAX_CONSUMER_POLL_DURATION_SECONDS))
                .iterator().next();

            consumer.unsubscribe();
            return record.value();
        }
    }
}
