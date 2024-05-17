package de.unimarburg.diz.labtofhir;

import de.unimarburg.diz.labtofhir.configuration.AdminClientProvider;
import de.unimarburg.diz.labtofhir.model.MappingInfo;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nullable;
import org.apache.kafka.clients.admin.Admin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.stream.binding.BindingsLifecycleController.State;
import org.springframework.cloud.stream.endpoint.BindingsEndpoint;
import org.springframework.context.event.EventListener;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Retryable
public class LabRunner implements ApplicationRunner {

    static final long RETRY_BACKOFF_PERIOD = 2_000L;
    private static final Logger LOG = LoggerFactory.getLogger(LabRunner.class);
    private final BindingsEndpoint endpoint;
    private final AdminClientProvider kafkaAdmin;
    private final MappingInfo mappingInfo;
    private final String updateGroup;
    private final RetryTemplate retryTemplate;

    @SuppressWarnings("checkstyle:LineLength")
    public LabRunner(BindingsEndpoint endpoint, AdminClientProvider kafkaAdmin,
        @Nullable MappingInfo mappingInfo, @Value(
        "${spring.cloud.stream.kafka.streams.binder.functions.update"
            + ".applicationId}") String updateGroup) {
        this.endpoint = endpoint;
        this.kafkaAdmin = kafkaAdmin;
        this.mappingInfo = mappingInfo;
        this.updateGroup = updateGroup;
        this.retryTemplate = setupRetryTemplate();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {

        if (mappingInfo != null) {

            // reset only on new update version
            if (!mappingInfo.resume()) {
                // reset update consumer

                try (var client = createAdminClient()) {
                    var offsets = client.listConsumerGroupOffsets(updateGroup)
                        .partitionsToOffsetAndMetadata().get();

                    if (!offsets.isEmpty()) {
                        LOG.info("Starting mapping update from {} to {}",
                            mappingInfo.update().getOldVersion(),
                            mappingInfo.update().getVersion());

                        // start update at the beginning
                        // delete consumer group
                        deleteUpdateConsumerGroup();
                    }
                }
            }

            // start update processor
            endpoint.changeState("update-in-0", State.STARTED);
        }

        // start regular lab processor
        endpoint.changeState("process-in-0", State.STARTED);
    }

    private Admin createAdminClient() {
        return kafkaAdmin.createClient();
    }

    RetryTemplate setupRetryTemplate() {

        return RetryTemplate.builder().customPolicy(new AlwaysRetryPolicy())
            .fixedBackoff(RETRY_BACKOFF_PERIOD)
            .withListener(new RetryListener() {
                @Override
                public <T, E extends Throwable> void onError(
                    RetryContext context, RetryCallback<T, E> callback,
                    Throwable throwable) {
                    LOG.debug(
                        "Delete Consumer group failed: {}. " + "Retrying {}",
                        Optional.ofNullable(throwable.getCause())
                            .orElse(throwable).getMessage(),
                        context.getRetryCount());
                }
            }).retryOn(ExecutionException.class).build();
    }

    public void stopAndDeleteUpdateConsumer() throws Exception {

        // start update processor
        endpoint.changeState("update-in-0", State.STOPPED);

        LOG.info("Update consumer stopped");

        // delete consumer group
        LOG.info("Deleting update consumer group...");
        deleteUpdateConsumerGroup();
    }


    private void deleteUpdateConsumerGroup() throws Exception {
        try (var client = createAdminClient()) {
            retryTemplate.execute(ctx -> client
                .deleteConsumerGroups(Collections.singleton(updateGroup)).all()
                .get());
        }
    }


    @EventListener
    @Async
    public void onApplicationEvent(UpdateCompleted event) {
        try {
            LOG.info("Update process complete");
            stopAndDeleteUpdateConsumer();
        } catch (Exception e) {
            LOG.error("stopAndDeleteUpdateConsumer", e);
            throw new RuntimeException("Failed to delete update consumer group",
                e);
        }
    }
}
