package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.hash.Hashing;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.util.TimestampPrefixedId;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ServiceRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract class BaseMapper<T> implements ValueMapper<T, Bundle> {

    private final IParser fhirParser;
    private final FhirProperties fhirProperties;
    private final Function<String, String> hasher =
        i -> Hashing.sha256().hashString(i, StandardCharsets.UTF_8).toString();
    private final Identifier identifierAssigner;
    private final List<CodeableConcept> serviceRequestCategory;
    private final CodeableConcept serviceRequestCode;
    private final Coding mapperTag;

    BaseMapper(FhirContext fhirContext, FhirProperties fhirProperties) {
        this.fhirProperties = fhirProperties;
        fhirParser = fhirContext.newJsonParser();
        identifierAssigner = new Identifier()
            .setSystem(fhirProperties.getSystems().getAssignerId())
            .setValue(fhirProperties.getSystems().getAssignerCode());
        serviceRequestCategory = initServiceRequestCategory();
        serviceRequestCode = initServiceRequestCode();
        this.mapperTag = new Coding().setSystem(
                fhirProperties().getSystems().getMapperTagSystem())
            .setCode(getMapperName());
    }

    abstract String getMapperName();

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

    protected String sanitizeId(String id) {
        // replace invalid characters with "-"
        var replaced =
            id.replaceAll("[^A-Za-z0-9\\-\\.]", "-");
        // max 64 characters
        return replaced.substring(0, Math.min(replaced.length(), 64));
    }

    protected Coding getMapperTag() {
        return mapperTag;
    }

    protected String createId(Identifier identifier,
                              DateTimeType dateTimeType) {
        return createId(identifier.getSystem(), identifier.getValue(),
            dateTimeType);
    }

    protected String createId(String idSystem, String id,
                              DateTimeType dateTimeType) {
        return TimestampPrefixedId.createNewIdentityValue(dateTimeType,
            hasher().apply(idSystem + "|" + id));
    }

    protected <TR extends Resource> List<TR> getBundleEntryResources(
        Bundle bundle,
        Class<TR> domainType) {
        return bundle.getEntry()
            .stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(domainType::isInstance)
            .map(domainType::cast)
            .collect(Collectors.toList());
    }

    protected void setPatient(String patientId, Bundle bundle) {

        getBundleEntryResources(bundle, ServiceRequest.class).forEach(
            r -> r.getSubject()
                .setReference(
                    getConditionalReference(ResourceType.Patient, patientId)));
        getBundleEntryResources(bundle, DiagnosticReport.class).forEach(
            r -> r.getSubject()
                .setReference(
                    getConditionalReference(ResourceType.Patient, patientId)));
        getBundleEntryResources(bundle, Observation.class).forEach(
            o -> o.getSubject()
                .setReference(
                    getConditionalReference(ResourceType.Patient, patientId)));

    }

    protected void setEncounter(String encounterId, Bundle bundle) {
        getBundleEntryResources(bundle, ServiceRequest.class).forEach(
            r -> r.getEncounter()
                .setReference(getConditionalReference(ResourceType.Encounter,
                    encounterId)));
        getBundleEntryResources(bundle, DiagnosticReport.class).forEach(
            r -> r.getEncounter()
                .setReference(getConditionalReference(ResourceType.Encounter,
                    encounterId)));
        getBundleEntryResources(bundle, Observation.class).forEach(
            o -> o.getEncounter()
                .setReference(getConditionalReference(ResourceType.Encounter,
                    encounterId)));
    }
}
