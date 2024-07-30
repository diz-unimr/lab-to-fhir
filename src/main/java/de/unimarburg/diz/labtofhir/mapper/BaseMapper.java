package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.hash.Hashing;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.MetaCode;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;

abstract class BaseMapper<T> implements ValueMapper<T, Bundle> {

    public static final Set<String> META_CODES =
        EnumSet.allOf(MetaCode.class).stream().map(Enum::toString)
            .collect(Collectors.toSet());
    

    private final IParser fhirParser;
    private final FhirProperties fhirProperties;
    private final Function<String, String> hasher =
        i -> Hashing.sha256().hashString(i, StandardCharsets.UTF_8).toString();
    private final Identifier identifierAssigner;
    private final LoincMapper loincMapper;

    BaseMapper(FhirContext fhirContext, FhirProperties fhirProperties,
        LoincMapper loincMapper) {
        this.fhirProperties = fhirProperties;
        this.loincMapper = loincMapper;
        fhirParser = fhirContext.newJsonParser();
        identifierAssigner = new Identifier()
            .setSystem(fhirProperties.getSystems().getAssignerId())
            .setValue(fhirProperties.getSystems().getAssignerCode());
    }

    protected Function<String, String> hasher() {
        return hasher;
    }

    protected Identifier identifierAssigner() {
        return identifierAssigner;
    }

    protected IParser fhirParser() {
        return fhirParser;
    }

    protected FhirProperties fhirProperties() {
        return fhirProperties;
    }

    protected LoincMapper loincMapper() {
        return loincMapper;
    }
}
