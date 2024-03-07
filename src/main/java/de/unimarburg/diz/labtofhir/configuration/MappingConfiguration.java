package de.unimarburg.diz.labtofhir.configuration;

import de.unimarburg.diz.labtofhir.mapper.LoincMapper;
import de.unimarburg.diz.labtofhir.model.LabOffsets;
import de.unimarburg.diz.labtofhir.model.MappingInfo;
import de.unimarburg.diz.labtofhir.model.MappingUpdate;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

@Configuration
@EnableConfigurationProperties
public class MappingConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(
        MappingConfiguration.class);
    private static final long MAX_CONSUMER_POLL_DURATION_SECONDS = 10L;

    @Bean
    public MappingProperties mappingProperties() {
        return new MappingProperties();
    }

    @Bean("mappingPackage")
    public Resource currentMappingFile(MappingProperties mp)
        throws IOException {
        return getMappingFile(mp
            .getLoinc()
            .getVersion(), mp
            .getLoinc()
            .getCredentials()
            .getUser(), mp
            .getLoinc()
            .getCredentials()
            .getPassword(), mp
            .getLoinc()
            .getProxy(), mp
            .getLoinc()
            .getLocal());
    }

    private Resource getMappingFile(
        @Value("${mapping.loinc.version}") String version,
        @Value("${mapping.loinc.credentials.user}") String user,
        @Value("${mapping.loinc.credentials.password}") String password,
        @Value("${mapping.loinc.proxy}") String proxyServer,
        @Value("${mapping.loinc.local}") String localPkg) throws IOException {

        if (StringUtils.isBlank(localPkg)) {
            // load from remote location
            LOG.info("Using LOINC mapping package from remote location");

            var provider = new BasicCredentialsProvider();
            var credentials = new UsernamePasswordCredentials(user, password);
            provider.setCredentials(AuthScope.ANY, credentials);

            var clientBuilder = HttpClientBuilder
                .create()
                .setDefaultCredentialsProvider(provider);

            if (!StringUtils.isBlank(proxyServer)) {
                clientBuilder.setProxy(HttpHost.create(proxyServer));
            }

            var response = clientBuilder
                .build()
                .execute(new HttpGet(String.format(
                    "https://gitlab.diz.uni-marburg.de/"
                        + "api/v4/projects/63/packages/generic/"
                        + "mapping-swl-loinc/%s/mapping-swl-loinc.zip",
                    version)));

            LOG.info("Package registry responded with: " + response
                .getStatusLine()
                .toString());

            var tmpFile = File.createTempFile("download", ".zip");
            StreamUtils.copy(response
                .getEntity()
                .getContent(), new FileOutputStream(tmpFile));

            return new FileSystemResource(tmpFile);

        } else {

            // load local file from classpath
            LOG.info("Using local LOINC mapping package from: {}", localPkg);
            return new FileSystemResource(
                new ClassPathResource(localPkg).getFile());
        }
    }

    @Bean
    public LoincMapper createMapper(FhirProperties fhirProperties,
        @Qualifier("mappingPackage") Resource mappingPackage) throws Exception {

        return new LoincMapper(fhirProperties, mappingPackage).initialize();
    }

    @Bean("mappingInfo")
    public MappingInfo buildMappingUpdate(LoincMapper loincMapper,
        MappingProperties mappingProperties, LabOffsets labOffsets,
        Consumer<String, MappingUpdate> consumer,
        Producer<String, MappingUpdate> producer)
        throws ExecutionException, InterruptedException, IOException {
        // check versions
        var configuredVersion = loincMapper
            .getMap()
            .getMetadata()
            .getVersion();

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
            LOG.info("Configured mapping version ({}) matches last version "
                    + "({}). " + "No update necesssary", configuredVersion,
                lastUpdate.getVersion());

            if (!labOffsets
                .updateOffsets()
                .isEmpty()) {
                // update in progress, continue
                return new MappingInfo(lastUpdate, true);
            }

            return null;
        }

        // 3. calculate diff of mapping versions and save new update
        // get last version's mapping
        var lastMap = LoincMapper.getSwlLoincMapping(
            getMappingFile(lastUpdate.getVersion(), mappingProperties
                .getLoinc()
                .getCredentials()
                .getUser(), mappingProperties
                .getLoinc()
                .getCredentials()
                .getPassword(), mappingProperties
                .getLoinc()
                .getProxy(), mappingProperties
                .getLoinc()
                .getLocal()));
        // ceate diff
        var updates = loincMapper
            .getMap()
            .diff(lastMap);
        var update = new MappingUpdate(configuredVersion,
            lastUpdate.getVersion(), updates);

        // save new mapping update
        saveMappingUpdate(producer, update);

        return new MappingInfo(update, false);
    }

    private void saveMappingUpdate(Producer<String, MappingUpdate> producer,
        MappingUpdate mappingUpdate)
        throws ExecutionException, InterruptedException {

        producer
            .send(new ProducerRecord<>("mapping", "lab-update", mappingUpdate))
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
                .iterator()
                .next();

            consumer.unsubscribe();
            return record.value();
        }
    }
}
