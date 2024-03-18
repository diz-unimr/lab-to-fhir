package de.unimarburg.diz.labtofhir.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.io.IOException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.core.io.FileSystemResource;

public class ResourceHelperTests {

    @Test
    void getMappingFileCallsPackageRegistry() throws IOException {

        try (var staticBuilder = Mockito.mockStatic(HttpClientBuilder.class)) {

            // mocks
            var builder = mock(HttpClientBuilder.class,
                InvocationOnMock::callRealMethod);
            var client = mock(CloseableHttpClient.class, RETURNS_DEEP_STUBS);
            var response = mock(CloseableHttpResponse.class,
                RETURNS_DEEP_STUBS);

            // stubs
            staticBuilder
                .when(HttpClientBuilder::create)
                .thenReturn(builder);
            when(builder.build()).thenReturn(client);
            when(client.execute(any(HttpGet.class))).thenReturn(response);
            when(response
                .getStatusLine()
                .toString()).thenReturn("200");
            when(response
                .getEntity()
                .getContent()).thenReturn(FileInputStream.nullInputStream());

            var resource = ResourceHelper.getMappingFile("1.0.0", "user",
                "password", "http://dummy-proxy", null);

            assertThat(resource).isInstanceOf(FileSystemResource.class);
        }
    }

}
