package de.unimarburg.diz.labtofhir.validator;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

public class FhirResourceLoader {

    private final static Logger log = LoggerFactory.getLogger(FhirResourceLoader.class);

    public static List<IBaseResource> loadFromDirectory(FhirContext ctx, String inputPath) {
        return loadFromDirectory(ctx, inputPath, "*");
    }

    public static List<IBaseResource> loadFromDirectory(FhirContext ctx, String inputPath,
        String fileNamePattern) {
        ResourcePatternResolver patternResolver = new PathMatchingResourcePatternResolver();
        try {
            var locationPattern = String.format("file:%s/**/%s", inputPath, fileNamePattern);
            log.info("Looking for input files in: {}", locationPattern);
            return Arrays.stream(patternResolver.getResources(locationPattern))
                .map(r -> tryParseResource(r, ctx))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (IOException e) {
            log.debug("Could not get file handle of resource", e);
        }
        return List.of();
    }

    private static IBaseResource tryParseResource(Resource fileResource, FhirContext ctx) {
        try {
            var parser = getParser(fileResource.getFile(), ctx);
            if (parser == null) {
                log.debug("Unable to get parser for file {}", fileResource.getFilename());
                return null;
            }
            return parser.parseResource(fileResource.getInputStream());

        } catch (IOException e) {
            log.debug("Could not get file handle of resource", e);
        } catch (DataFormatException de) {
            // not a FHIR resource
            return null;
        }
        return null;
    }

    private static IParser getParser(File file, FhirContext ctx) {
        String fileType = FilenameUtils.getExtension(file.getName())
            .toLowerCase();
        if (fileType.equals("xml")) {
            return ctx.newXmlParser();
        } else if (fileType.equals("json")) {
            return ctx.newJsonParser();
        }
        return null;
    }
}
