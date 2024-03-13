package de.unimarburg.diz.labtofhir;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.unimarburg.diz.labtofhir.configuration.AdminClientProvider;
import de.unimarburg.diz.labtofhir.model.MappingInfo;
import de.unimarburg.diz.labtofhir.model.MappingUpdate;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.binding.BindingsLifecycleController.State;
import org.springframework.cloud.stream.endpoint.BindingsEndpoint;

@ExtendWith(MockitoExtension.class)
public class LabRunnerTests {

    @Mock
    private BindingsEndpoint endpoint;
    @Mock
    private AdminClientProvider kafkaAdmin;


    @Test
    void runAlwaysStartsLabBinder() throws Exception {

        // arrange
        var runner = setupRunner(null);

        // act
        runner.run(null);

        // assert
        verify(endpoint).changeState("process-in-0", State.STARTED);
        verify(endpoint, never()).changeState("update-in-0", State.STARTED);
    }

    @Test
    void runStartsUpdateBinderOnResume() throws Exception {

        var runner = setupRunner(new MappingInfo(null, true));

        // act
        runner.run(null);

        verify(endpoint).changeState("process-in-0", State.STARTED);
        verify(endpoint).changeState("update-in-0", State.STARTED);
    }

    @Test
    void runResetsUpdateConsumerOnExistingOffsets() throws Exception {

        // admin client
        var admin = setupAdminClient();

        when(admin
            .listConsumerGroupOffsets(anyString())
            .partitionsToOffsetAndMetadata()
            .get()).thenReturn(Map.of(new TopicPartition("lab-input", 0),
            new OffsetAndMetadata(0, null)));

        var runner = setupRunner(
            new MappingInfo(new MappingUpdate("2.0", "1.0", List.of("NA")),
                false));

        // act
        runner.run(null);

        // verify delete consumer group
        verify(admin
            .deleteConsumerGroups(anyCollection())
            .all()).get();

        verify(endpoint).changeState("process-in-0", State.STARTED);
        verify(endpoint).changeState("update-in-0", State.STARTED);
    }

    private LabRunner setupRunner(MappingInfo mappingInfo) {
        return new LabRunner(endpoint, kafkaAdmin, mappingInfo, "lab-update");
    }

    private Admin setupAdminClient() {
        // admin client
        var admin = mock(Admin.class, RETURNS_DEEP_STUBS);
        when(kafkaAdmin.createClient()).thenReturn(admin);
        return admin;
    }

}
