package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.hash.Hashing;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.util.TimestampPrefixedId;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.Type;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

abstract class BaseMapper<T> implements ValueMapper<T, Bundle> {

    private static final int MAX_IDENTIFIER_LENGTH = 64;
    private final IParser fhirParser;
    private final FhirProperties fhirProperties;
    private final Function<String, String> hasher =
        i -> Hashing.sha256().hashString(i, StandardCharsets.UTF_8).toString();
    private final Identifier identifierAssigner;
    private final List<CodeableConcept> serviceRequestCategory;
    private final CodeableConcept serviceRequestCode;
    private final Coding mapperTag;
    private final Predicate<T> filter;

    BaseMapper(FhirContext fhirContext, FhirProperties fhirProperties,
               DateFilter dateFilter) {
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
        this.filter = dateFilter != null ? createFilter(dateFilter) : null;
    }

    protected List<CodeableConcept> getReportCategory() {
        return List.of(new CodeableConcept()
            .addCoding(
                new Coding().setSystem("http://loinc.org").setCode("26436-6"))
            .addCoding(new Coding().setSystem(
                    "http://terminology.hl7.org/CodeSystem/v2-0074")
                .setCode("LAB")));
    }

    protected Meta getMeta(String resourceType) {
        return new Meta().addProfile(
                "https://www.medizininformatik-initiative"
                    + ".de/fhir/core/modul-labor/StructureDefinition/"
                    + resourceType + "Lab")
            .addTag(getMapperTag())
            .setSource("#swisslab");
    }

    protected CodeableConcept getObservationCategory() {
        return new CodeableConcept().addCoding(
                new Coding().setSystem("http://loinc.org")
                    .setCode("26436-6"))
            .addCoding(new Coding().setSystem("http://terminology.hl7"
                    + ".org/CodeSystem/observation-category")
                .setCode("laboratory"));
    }

    abstract String getMapperName();

    @Override
    public final Bundle apply(T report) {

        if (filter == null || filter.test(report)) {
            return map(report);
        }

        // skip
        return null;
    }

    abstract Bundle map(T report);

    abstract Predicate<T> createFilter(DateFilter filter);

    protected boolean createDateTimeFilter(DateTimeType date,
                                           DateFilter filter) {

        var targetDate = date.getValue().toInstant().atZone(
            ZoneId.systemDefault()).toLocalDate();
        var threshold = filter.date();


        return switch (filter.comparator()) {
            case "<" -> targetDate.isBefore(threshold);
            case "<=" -> targetDate.isBefore(threshold)
                || targetDate.isEqual(threshold);
            case ">" -> targetDate.isAfter(threshold);
            case ">=" -> targetDate.isAfter(threshold)
                || targetDate.equals(threshold);
            case "=" -> targetDate.isEqual(threshold);
            default -> throw new IllegalStateException(
                "Unexpected filter comparator: " + filter.comparator());
        };
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

    protected Reference getObservationsReference(Observation obs) {
        return new Reference(
            getConditionalReference(ResourceType.Observation,
                obs.getIdentifierFirstRep()));
    }

    protected void addResourceToBundle(Bundle bundle, DomainResource resource,
                                       Identifier identifier) {

        var idElement = resource.getIdElement()
            .getValue();
        bundle.addEntry()
            .setResource(resource)
            .setRequest(
                new Bundle.BundleEntryRequestComponent().setMethod(
                        Bundle.HTTPVerb.PUT)
                    .setUrl(getConditionalReference(resource.getResourceType(),
                        identifier)));
    }

    protected String getConditionalReference(ResourceType resourceType,
                                             String identifierValue) {

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

        return getConditionalReference(resourceType,
            new Identifier().setSystem(idSystem).setValue(identifierValue));
    }

    protected String getConditionalReference(ResourceType resourceType,
                                             Identifier identifier) {

        return String.format("%s?identifier=%s|%s", resourceType,
            identifier.getSystem(),
            identifier.getValue());
    }

    protected String sanitizeId(String id) {
        // replace invalid characters with "-"
        var replaced =
            id.replaceAll("[^A-Za-z0-9\\-\\.]", "-");
        // max 64 characters
        return replaced.substring(0,
            Math.min(replaced.length(), MAX_IDENTIFIER_LENGTH));
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

    protected Quantity parseQuantity(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }

        value = value.trim();

        if (NumberUtils.isCreatable(value)) {
            return new Quantity(
                NumberUtils.createDouble(value));
        }

        // check first character for comparator value
        var comp = value.charAt(0);
        var valuePart = value.substring(1)
            .trim();

        if ((comp == '<' || comp == '>') && NumberUtils.isCreatable(
            valuePart)) {
            return new Quantity(
                NumberUtils.createDouble(valuePart)).setComparator(
                Quantity.QuantityComparator.fromCode(String.valueOf(comp)));
        }

        return null;
    }

    protected Observation createObservation(String id) {
        var identifierType = new CodeableConcept().addCoding(
            new Coding().setSystem(
                    "http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("OBI"));

        var obs = new Observation();
        // id
        obs.setId(id);
        // meta data
        obs.setMeta(getMeta(ResourceType.Observation.name()));

        // identifier
        return obs.addIdentifier(new Identifier().setType(identifierType)
            .setSystem(fhirProperties().getSystems()
                .getObservationId())
            .setValue(id)
            .setAssigner(new Reference()
                .setIdentifier(identifierAssigner())));
    }

    protected Observation.ObservationReferenceRangeComponent
    parseReferenceRange(String rangeString, Type value) {

        if (rangeString == null) {
            return null;
        }

        var refRange =
            new Observation.ObservationReferenceRangeComponent().setText(
                rangeString);

        // fix trim on open bounded reference string
        if (rangeString.startsWith("-")) {
            rangeString = " " + rangeString;
        } else if (rangeString.endsWith("-")) {
            rangeString = rangeString + " ";
        }

        var parts = rangeString.split("-", 2);

        if (parts.length == 2 && value instanceof Quantity q) {
            var left = parts[0];
            var right = parts[1];

            // set code and system according to its quantity value
            var low = parseQuantity(left);
            var high = parseQuantity(right);

            // lower and higher bounds need to be empty or quantity
            if ((low == null && !StringUtils.isBlank(left))
                || (high == null && !StringUtils.isBlank(right))) {
                return refRange;
            }

            // SimpleQuantity does not allow comparator (sqty-1)
            if (low != null) {
                refRange.setLow(
                    new SimpleQuantity().setValue(low.getValue())
                        .setSystem(q.getSystem()).setCode(q.getCode())
                        .setUnit(q.getUnit()));
            }
            if (high != null) {
                refRange.setHigh(
                    new SimpleQuantity().setValue(high.getValue())
                        .setSystem(q.getSystem()).setCode(q.getCode())
                        .setUnit(q.getUnit()));
            }
        }

        return refRange;
    }
}
