package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.hash.Hashing;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.MetaCode;
import de.unimarburg.diz.labtofhir.util.TimestampPrefixedId;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryRequestComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationReferenceRangeComponent;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Quantity.QuantityComparator;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MiiLabReportMapper
    implements ValueMapper<LaboratoryReport, Bundle> {

    public static final Set<String> META_CODES = EnumSet.allOf(MetaCode.class)
        .stream()
        .map(Enum::toString)
        .collect(Collectors.toSet());
    private static final Logger LOG =
        LoggerFactory.getLogger(MiiLabReportMapper.class);
    private final IParser fhirParser;
    private final FhirProperties fhirProperties;
    private final Function<String, String> hasher = i -> Hashing.sha256()
        .hashString(i, StandardCharsets.UTF_8)
        .toString();
    private final Identifier identifierAssigner;
    private final LoincMapper loincMapper;

    public MiiLabReportMapper(FhirContext fhirContext,
        FhirProperties fhirProperties, LoincMapper loincMapper) {
        this.fhirProperties = fhirProperties;
        this.loincMapper = loincMapper;
        fhirParser = fhirContext.newJsonParser();
        identifierAssigner = new Identifier().setSystem(
                fhirProperties.getSystems()
                    .getAssignerId())
            .setValue(fhirProperties.getSystems()
                .getAssignerCode());
    }

    @Override
    public Bundle apply(LaboratoryReport report) {
        LOG.debug("Mapping LaboratoryReport with id:{} and order number:{}",
            report.getId(), report.getReportIdentifierValue());
        Bundle bundle = new Bundle();
        try {

            // set meta information
            bundle.setId(report.getReportIdentifierValue());
            bundle.setType(BundleType.TRANSACTION);

            processMetaResults(report)
                // service request
                .mapServiceRequest(report.getResource(), bundle);

            // diagnostic report
            var mappedReport = mapDiagnosticReport(report.getResource(), bundle)

                // observations
                .setResult((report.getObservations()
                    .stream()
                    // map observations
                    .map(this::mapObservation)
                    .map(o -> loincMapper.map(o, report.getMetaCode()))

                    // add to bundle
                    .peek(o -> addResourceToBundle(bundle, o))
                    // set result references
                    .map(o -> new Reference(
                        getConditionalReference(ResourceType.Observation,
                            o.getId())))).collect(Collectors.toList()));

            if (mappedReport.getResult()
                .isEmpty()) {
                // report contains no observations
                LOG.info(
                    "No Observations after mapping for LaboratoryReport with "
                        + "id {} and order number {}. Discarding.",
                    report.getId(), report.getReportIdentifierValue());
                return null;
            }

            setPatient(report, bundle);
            setEncounter(report, bundle);
        } catch (Exception e) {
            LOG.error(
                "Mapping failed for LaboratoryReport with id {} and order "
                    + "number {}", report.getId(),
                report.getReportIdentifierValue(), e);
            // TODO add metrics
            return null;
        }

        LOG.debug("Mapped successfully to FHIR bundle: id:{}, order number:{}",
            report.getId(), report.getReportIdentifierValue());
        LOG.trace("FHIR bundle: {}", fhirParser.encodeResourceToString(bundle));

        return bundle;
    }

    private MiiLabReportMapper processMetaResults(LaboratoryReport report) {
        var metaObs = report.getObservations()
            .stream()
            .filter(x -> META_CODES.contains(x.getCode()
                .getCoding()
                .stream()
                .findFirst()
                .orElse(new Coding())
                .getCode()))
            .toList();

        if (!metaObs.isEmpty()) {
            // only one meta code currently supported
            var firstMetaObs = metaObs.get(0);
            if (firstMetaObs.hasValueStringType()) {
                report.setMetaCode(firstMetaObs.getValueStringType()
                    .getValue());
            }

            // remove from DiagnosticReport
            report.getObservations()
                .removeIf(x -> Objects.equals(x, firstMetaObs));
        }

        return this;
    }


    private DiagnosticReport mapDiagnosticReport(DiagnosticReport labReport,
        Bundle bundle) {
        var identifierType = new CodeableConcept().addCoding(
            new Coding().setSystem(
                    "http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("FILL"));
        var identifierValue = labReport.getIdentifierFirstRep()
            .getValue();

        var report = new DiagnosticReport();

        // id
        report.setId(identifierValue);
        // meta data
        report.setMeta(new Meta().setProfile(List.of(new CanonicalType(
                "https://www.medizininformatik-initiative"
                    + ".de/fhir/core/modul-labor/StructureDefinition"
                    + "/DiagnosticReportLab")))
            .setSource("#swisslab"));

        // identifier
        report.setIdentifier(List.of(new Identifier().setType(identifierType)
                .setSystem(fhirProperties.getSystems()
                    .getDiagnosticReportId())
                .setValue(identifierValue)
                .setAssigner(
                    new Reference().setIdentifier(getIdentifierAssigner()))))

            // basedOn
            .setBasedOn(List.of(new Reference(
                getConditionalReference(ResourceType.ServiceRequest, createId(
                    fhirProperties.getSystems()
                        .getServiceRequestId(), identifierValue,
                    labReport.getEffectiveDateTimeType())))))

            // status
            .setStatus(labReport.getStatus())

            // category
            .setCategory(List.of(new CodeableConcept().addCoding(
                    new Coding().setSystem("http://loinc.org")
                        .setCode("26436" + "-6"))

                .addCoding(new Coding().setSystem(
                        "http://terminology.hl7.org/CodeSystem/v2-0074")
                    .setCode("LAB"))
                // with local category
                .addCoding(labReport.getCategoryFirstRep()
                    .getCodingFirstRep())))

            // code
            .setCode(labReport.getCode())

            // status
            .setStatus(labReport.getStatus())

            // effective
            .setEffective(labReport.getEffectiveDateTimeType())

            // issued
            .setIssuedElement(
                new InstantType(labReport.getEffectiveDateTimeType()));

        // add to bundle
        addResourceToBundle(bundle, report);

        return report;
    }

    private Observation mapObservation(Observation source) {
        var identifierType = new CodeableConcept().addCoding(
            new Coding().setSystem(
                    "http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("OBI"));
        var identifierValue = createId(source.getIdentifierFirstRep(),
            source.getEffectiveDateTimeType());

        var obs = new Observation();
        // id
        obs.setId(identifierValue);
        // meta data
        obs.setMeta(new Meta().setProfile(List.of(new CanonicalType(
                "https://www.medizininformatik-initiative"
                    + ".de/fhir/core/modul-labor/StructureDefinition"
                    + "/ObservationLab")))
            .setSource("#swisslab"));

        // identifier
        obs.addIdentifier(new Identifier().setType(identifierType)
                .setSystem(fhirProperties.getSystems()
                    .getObservationId())
                .setValue(identifierValue)
                .setAssigner(
                    new Reference().setIdentifier(getIdentifierAssigner())))

            // status
            .setStatus(source.getStatus())
            // category
            .setCategory(List.of(new CodeableConcept().addCoding(
                    new Coding().setSystem("http://loinc.org")
                        .setCode("26436" + "-6"))
                .addCoding(new Coding().setSystem("http://terminology.hl7"
                        + ".org/CodeSystem/observation-category")
                    .setCode("laboratory"))
                // with local category
                .addCoding(source.getCategoryFirstRep()
                    .getCodingFirstRep())))

            // local coding: set system
            .setCode(new CodeableConcept().addCoding(source.getCode()
                .getCodingFirstRep()
                .setSystem(fhirProperties.getSystems()
                    .getLaboratorySystem())))
            .setEffective(source.getEffective())
            .setValue(parseValue(source))
            // interpretation
            .setInterpretation(source.getInterpretation()
                .stream()
                .map(cc -> new CodeableConcept().setCoding(
                    // set system on each coding
                    cc.getCoding()
                        .stream()
                        .map(c -> c.setSystem(
                            "http://terminology.hl7.org/CodeSystem/v3"
                                + "-ObservationInterpretation"))
                        .collect(Collectors.toList())))
                .collect(Collectors.toList()))
            // map reference range to simple quantity with value only
            .setReferenceRange(source.getReferenceRange()
                .stream()
                .map(this::harmonizeRangeQuantities)
                .collect(Collectors.toList()))
            // note
            .setNote(source.getNote());

        return obs;
    }

    private ObservationReferenceRangeComponent harmonizeRangeQuantities(
        ObservationReferenceRangeComponent rangeComponent) {
        if (rangeComponent.hasLow()) {
            ensureCodeIsSet(rangeComponent.getLow());
        }
        if (rangeComponent.hasHigh()) {
            ensureCodeIsSet(rangeComponent.getHigh());
        }
        return rangeComponent;
    }

    private Type parseValue(Observation obs) {
        if (obs.hasValueStringType()) {
            // fix numeric value with comparator in "valueString"
            var valueString = obs.getValueStringType()
                .getValue();

            // check first character for comparator value
            var comp = valueString.charAt(0);
            var valuePart = valueString.substring(1)
                .trim();

            if ((comp == '<' || comp == '>') && NumberUtils.isCreatable(
                valuePart)) {
                return new Quantity(
                    NumberUtils.createDouble(valuePart)).setComparator(
                    QuantityComparator.fromCode(String.valueOf(comp)));
            }
        } else if (obs.hasValueQuantity()) {
            ensureCodeIsSet(obs.getValueQuantity());
        }

        return obs.getValue();

    }

    /**
     * Ensures Quantity.code is set if unit is provided.
     */
    private void ensureCodeIsSet(Quantity quantity) {
        if (quantity.hasUnit() && !quantity.hasCode()) {
            // every code needs a system :)
            quantity.setCode(quantity.getUnit())
                .setSystem(fhirProperties.getSystems()
                    .getLaboratoryUnitSystem());
        }
    }


    public MiiLabReportMapper mapServiceRequest(DiagnosticReport report,
        Bundle bundle) {
        // TODO clarify intention: using this as a wrapper resource in order
        //  to be conform to MII profiles
        var identifierType = new CodeableConcept(new Coding().setSystem(
                "http://terminology.hl7.org/CodeSystem/v2-0203")
            .setCode("PLAC"));
        var identifierValue = createId(fhirProperties.getSystems()
            .getServiceRequestId(), report.getIdentifierFirstRep()
            .getValue(), report.getEffectiveDateTimeType());

        var serviceRequest = new ServiceRequest();
        // id
        serviceRequest.setId(identifierValue);
        // meta
        serviceRequest.setMeta(new Meta().addProfile(
                "https://www.medizininformatik-initiative"
                    + ".de/fhir/core/modul-labor/StructureDefinition"
                    + "/ServiceRequestLab")
            .setSource("#swisslab"));

        serviceRequest.setIdentifier(List.of(new Identifier().setSystem(
                    fhirProperties.getSystems()
                        .getServiceRequestId())
                .setType(identifierType)
                .setValue(identifierValue)
                .setAssigner(
                    new Reference().setIdentifier(getIdentifierAssigner()))))

            // authoredOn
            // uses report effective (date)
            .setAuthoredOnElement(report.getEffectiveDateTimeType())

            // status & intent
            .setStatus(ServiceRequest.ServiceRequestStatus.COMPLETED)
            .setIntent(ServiceRequest.ServiceRequestIntent.ORDER)

            // category
            .setCategory(List.of(new CodeableConcept(new Coding().setSystem(
                    "http://terminology.hl7"
                        + ".org/CodeSystem/observation-category")
                .setCode("laboratory"))))

            // code
            .setCode(new CodeableConcept().setCoding(List.of(
                new Coding().setSystem("http://snomed.info/sct")
                    .setCode("59615004"))));

        // add to bundle
        addResourceToBundle(bundle, serviceRequest);
        return this;
    }


    /**
     * Set the encounter reference for {@link DiagnosticReport} and
     * {@link Observation}
     */
    private void setEncounter(LaboratoryReport report, Bundle bundle) {
        if (report.getResource()
            .getEncounter()
            .getResource() == null) {
            throw new IllegalArgumentException(String.format(
                "Missing referenced encounter resource in report '%s'. "
                    + "Reference is '%s'", report.getId(), report.getResource()
                    .getEncounter()
                    .getReference()));
        }

        var encounterId = ((Encounter) report.getResource()
            .getEncounter()
            .getResource()).getIdentifierFirstRep()
            .getValue();

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

    private String getConditionalReference(ResourceType resourceType,
        String id) {

        String idSystem;
        switch (resourceType) {
            case Patient -> idSystem = fhirProperties.getSystems()
                .getPatientId();
            case Encounter -> idSystem = fhirProperties.getSystems()
                .getEncounterId();
            case ServiceRequest -> idSystem = fhirProperties.getSystems()
                .getServiceRequestId();
            case DiagnosticReport -> idSystem = fhirProperties.getSystems()
                .getDiagnosticReportId();
            case Observation -> idSystem = fhirProperties.getSystems()
                .getObservationId();
            default -> throw new IllegalArgumentException(
                "Unsupported resource type when building conditional "
                    + "reference");
        }

        return String.format("%s?identifier=%s|%s", resourceType, idSystem, id);
    }

    /**
     * Set the subject reference for {@link DiagnosticReport} and
     * {@link Observation}
     */
    public void setPatient(LaboratoryReport report, Bundle bundle) {
        if (report.getResource()
            .getSubject()
            .getResource() == null) {
            throw new IllegalArgumentException(String.format(
                "Missing referenced patient resource in report '%s'. "
                    + "Reference is '%s'", report.getId(), report.getResource()
                    .getSubject()
                    .getReference()));
        }

        var patientId = ((Patient) report.getResource()
            .getSubject()
            .getResource()).getIdentifierFirstRep()
            .getValue();

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

    public <T> List<T> getBundleEntryResources(Bundle bundle,
        Class<T> domainType) {
        return bundle.getEntry()
            .stream()
            .map(BundleEntryComponent::getResource)
            .filter(domainType::isInstance)
            .map(domainType::cast)
            .collect(Collectors.toList());
    }

    private Identifier getIdentifierAssigner() {
        return identifierAssigner;
    }

    private String createId(Identifier identifier, DateTimeType dateTimeType) {
        return createId(identifier.getSystem(), identifier.getValue(),
            dateTimeType);
    }

    private String createId(String idSystem, String id,
        DateTimeType dateTimeType) {
        return TimestampPrefixedId.createNewIdentityValue(dateTimeType,
            hasher.apply(idSystem + "|" + id));
    }

    private void addResourceToBundle(Bundle bundle, DomainResource resource) {
        var idElement = resource.getIdElement()
            .getValue();
        bundle.addEntry()
            .setFullUrl(resource.getResourceType()
                .name() + "/" + idElement)
            .setResource(resource)
            .setRequest(
                new BundleEntryRequestComponent().setMethod(HTTPVerb.PUT)
                    .setUrl(getConditionalReference(resource.getResourceType(),
                        idElement)));
    }

}

