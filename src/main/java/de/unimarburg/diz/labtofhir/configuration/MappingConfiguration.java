package de.unimarburg.diz.labtofhir.configuration;

import de.unimarburg.diz.labtofhir.util.ResourceHelper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@EnableConfigurationProperties
public class MappingConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(
        MappingConfiguration.class);

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

        return ResourceHelper.getMappingFile(version, user, password,
            proxyServer, localPkg);
    }
}
