package de.unimarburg.diz.labtofhir.configuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

@Configuration
public class MappingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MappingConfiguration.class);

    @Bean("mappingPackage")
    public Resource getMappingFile(@Value("${mapping.loinc.version}") String version,
        @Value("${mapping.loinc.credentials.user}") String user,
        @Value("${mapping.loinc.credentials.password}") String password,
        @Value("${mapping.loinc.local}") String localPkg
    )
        throws IOException {

        if (StringUtils.isBlank(localPkg)) {
            // load from remote location
            log.info("Using LOINC mapping package from remote location");

            var provider = new BasicCredentialsProvider();
            var credentials
                = new UsernamePasswordCredentials(user, password);
            provider.setCredentials(AuthScope.ANY, credentials);

            var client = HttpClientBuilder.create()
                .setDefaultCredentialsProvider(provider)
                .setProxy(new HttpHost("194.25.45.45",8080,"http"))
                .build();

            var response = client.execute(
                new HttpGet(
                    String.format(
                        "https://gitlab.diz.uni-marburg.de/api/v4/projects/63/packages/generic/mapping-swl-loinc/%s/mapping-swl-loinc.zip",
                        version)));

            var tmpFile = File.createTempFile("download", ".zip");
            StreamUtils.copy(response.getEntity().getContent(), new FileOutputStream(tmpFile));

            return new FileSystemResource(tmpFile);

        } else {

            // load local file from classpath
            log.info("Using local LOINC mapping package from: {}", localPkg);
            return new FileSystemResource(new ClassPathResource(localPkg).getFile());
        }
    }
}
