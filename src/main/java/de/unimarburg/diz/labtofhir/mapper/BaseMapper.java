package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.hash.Hashing;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.MetaCode;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ResourceType;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract class BaseMapper<T> implements ValueMapper<T, Bundle> {

    public static final Set<String> META_CODES =
        EnumSet.allOf(MetaCode.class).stream().map(Enum::toString)
            .collect(Collectors.toSet());

    private final IParser fhirParser;
    private final FhirProperties fhirProperties;
    private final Function<String, String> hasher =
        i -> Hashing.sha256().hashString(i, StandardCharsets.UTF_8).toString();
    private final Identifier identifierAssigner;
    private final List<CodeableConcept> serviceRequestCategory;
    private final CodeableConcept serviceRequestCode;

    BaseMapper(FhirContext fhirContext, FhirProperties fhirProperties) {
        this.fhirProperties = fhirProperties;
        fhirParser = fhirContext.newJsonParser();
        identifierAssigner = new Identifier()
            .setSystem(fhirProperties.getSystems().getAssignerId())
            .setValue(fhirProperties.getSystems().getAssignerCode());
        serviceRequestCategory = initServiceRequestCategory();
        serviceRequestCode = initServiceRequestCode();
    }

    private CodeableConcept initServiceRequestCode() {
        return new CodeableConcept().setCoding(List.of(
            new Coding().setSystem("http://snomed.info/sct")
                .setCode("59615004")));
    }

    protected List<CodeableConcept> getServiceRequestCategory() {
        return serviceRequestCategory;
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


    private List<CodeableConcept> initServiceRequestCategory() {
        return List.of(new CodeableConcept(new Coding().setSystem(
                "http://terminology.hl7"
                    + ".org/CodeSystem/observation-category")
            .setCode("laboratory")));
    }

    protected CodeableConcept getServiceRequestCode() {
        return serviceRequestCode;
    }

    protected void addResourceToBundle(Bundle bundle, DomainResource resource) {
        var idElement = resource.getIdElement()
            .getValue();
        bundle.addEntry()
            .setFullUrl(resource.getResourceType()
                .name() + "/" + idElement)
            .setResource(resource)
            .setRequest(
                new Bundle.BundleEntryRequestComponent().setMethod(
                        Bundle.HTTPVerb.PUT)
                    .setUrl(getConditionalReference(resource.getResourceType(),
                        idElement)));
    }

    protected String getConditionalReference(ResourceType resourceType,
                                             String id) {

        String idSystem;
        switch (resourceType) {
            case Patient -> idSystem = fhirProperties().getSystems()
                .getPatientId();
            case Encounter -> idSystem = fhirProperties().getSystems()
                .getEncounterId();
            case ServiceRequest -> idSystem = fhirProperties().getSystems()
                .getServiceRequestId();
            case DiagnosticReport -> idSystem = fhirProperties().getSystems()
                .getDiagnosticReportId();
            case Observation -> idSystem = fhirProperties().getSystems()
                .getObservationId();
            default -> throw new IllegalArgumentException(
                "Unsupported resource type when building conditional "
                    + "reference");
        }

        return String.format("%s?identifier=%s|%s", resourceType, idSystem, id);
    }
}
