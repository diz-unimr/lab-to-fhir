package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.hash.Hashing;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import de.unimarburg.diz.labtofhir.model.LoincMappingResult;
import de.unimarburg.diz.labtofhir.model.MappingContainer;
import de.unimarburg.diz.labtofhir.model.MappingResult;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.BaseReference;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Narrative.NarrativeStatus;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MiiLabReportMapper implements
    ValueMapper<LaboratoryReport, MappingContainer<LaboratoryReport, Bundle>> {

    private final static Logger log = LoggerFactory.getLogger(MiiLabReportMapper.class);
    private static final String META_CODE_POCT = "MTYP-POCT";
    private static final String META_CODE_DIFFART = "DIFFART";
    private final IParser fhirParser;
    private final FhirProperties fhirProperties;
    private final Function<String, String> hasher = i -> Hashing.sha256()
        .hashString(i, StandardCharsets.UTF_8)
        .toString();
    private final Identifier identifierAssigner;
    private final Set<String> metaCodes = Set.of(META_CODE_DIFFART, META_CODE_POCT);
    private final LoincMapper loincMapper;

    @Autowired
    public MiiLabReportMapper(FhirContext fhirContext, FhirProperties fhirProperties,
        LoincMapper loincMapper) {
        this.loincMapper = loincMapper;
        this.fhirProperties = fhirProperties;
        fhirParser = fhirContext.newJsonParser();
        identifierAssigner = new Identifier().setSystem(fhirProperties.getSystems()
            .getAssignerId())
            .setValue(fhirProperties.getSystems()
                .getAssignerCode());
    }

    private <T extends Resource> T createWithId(Class<T> resourceType, String id)
        throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        var resource = resourceType.getConstructor().newInstance();
        resource.setId(resourceType.getSimpleName() + "/" + id);

        return resource;
    }

    private <T extends DomainResource> void setNarrative(T resource) {
        var narrative = fhirParser.setPrettyPrint(true)
            .encodeResourceToString(resource);
        resource.getText()
            .setStatus(NarrativeStatus.GENERATED);
        resource.getText()
            .setDivAsString(narrative);
    }

    @Override
    public MappingContainer<LaboratoryReport, Bundle> apply(LaboratoryReport report) {
        log.debug("Mapping LaboratoryReport: {}", report);
        Bundle bundle = new Bundle();
        var mappingContainer = new MappingContainer<>(report, bundle);

        try {

            // set meta information
            bundle.setId(report.getReportIdentifierValue());
            bundle.setType(BundleType.TRANSACTION);

            processMetaResults(report)
                // diagnostic report
                .mapDiagnosticReport(report.getResource(), bundle)

                // observations
                .setResult((report.getResource()
                    .getResult()
                    .stream()
                    .map(BaseReference::getResource)
                    .filter(Observation.class::isInstance)
                    .map(Observation.class::cast)
                    // map observations
                    .map(this::mapObservation)
                    .map(o -> mapLoincUcum(o, mappingContainer))
                    .map(this::convertLoincUcum)
                    // add to bundle
                    .peek(o -> addResourceToBundle(bundle, o))
                    // set result references
                    .map(Reference::new)).collect(Collectors.toList()));

            setPatient(report, bundle);
            setEncounter(report, bundle);

            if (fhirProperties.getGenerateNarrative()) {
                generateNarratives(bundle);
            }


        } catch (Exception e) {
            log.error("Mapping failed for LaboratoryReport with id {} and order number {}",
                report.getId(), report.getReportIdentifierValue(), e);
            // TODO add metrics
            return mappingContainer.withException(e);
        }

        log.debug("Mapped successfully to FHIR bundle: {}",
            fhirParser.encodeResourceToString(bundle));

        return mappingContainer;
    }

    private MiiLabReportMapper processMetaResults(
        LaboratoryReport report) {
        var metaObs = report.getResource().getResult().stream()
            .map(BaseReference::getResource).map(Observation.class::cast)
            .filter(x -> metaCodes.contains(
                x.getCode().getCoding().stream().findFirst().orElse(new Coding()).getCode()))
            .collect(Collectors.toList());

        // only one meta code currently supported
        if (!metaObs.isEmpty()) {
            report.setMetaCode(metaObs.get(0).getCode().getCoding().get(0).getCode());

            // remove from results
            report.getResource().getResult()
                .removeIf(x -> Objects.equals(x.getResource(), metaObs.get(0)));
        }

        return this;
    }

    private void generateNarratives(Bundle bundle) {
        bundle.getEntry()
            .stream()
            .map(BundleEntryComponent::getResource)
            .filter(DomainResource.class::isInstance)
            .map(DomainResource.class::cast)
            .forEach(this::setNarrative);
    }

    public Observation convertLoincUcum(Observation observation) {
        // TODO remove?
        return observation;

    }

    private Observation mapLoincUcum(Observation obs,
        MappingContainer<LaboratoryReport, Bundle> mappingContainer) {

        // map loinc code and ucum (if valueQuantity)
        var result = loincMapper
            .mapCodeAndQuantity(obs, mappingContainer.getSource().getMetaCode());
        if (result != LoincMappingResult.SUCCESS) {
            mappingContainer.withResultType(MappingResult.MISSING_CODE_MAPPING);
        }

        return obs;
    }

    private DiagnosticReport mapDiagnosticReport(DiagnosticReport labReport, Bundle bundle) {
        var identifierType = new CodeableConcept().addCoding(
            new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("FILL"));
        var identifierValue = labReport.getIdentifierFirstRep().getValue();

        var report = new DiagnosticReport();
        // id
        report.setId(identifierValue);
        // meta data
        report.setMeta(new Meta().setProfile(List.of(new CanonicalType(
            "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/DiagnosticReportLab"))));

        // identifier
        report.setIdentifier(List.of(new Identifier().setType(identifierType)
            .setSystem(fhirProperties.getSystems()
                .getDiagnosticReportId())
            .setValue(identifierValue)
            .setAssigner(new Reference().setIdentifier(getIdentifierAssigner()))))

            // status
            .setStatus(labReport.getStatus())

            // category
            .setCategory(List.of(new CodeableConcept()
                .addCoding(new Coding().setSystem("http://loinc.org").setCode("26436-6")).addCoding(
                    new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0074").setCode(
                        "LAB"))
                // with local category
                .addCoding(labReport.getCategoryFirstRep().getCodingFirstRep())))

            // code
            .setCode(labReport.getCode())

            // status
            .setStatus(labReport.getStatus())

            // effective
            .setEffective(labReport.getEffectiveDateTimeType())

            // issued
            .setIssuedElement(new InstantType(labReport.getEffectiveDateTimeType()));

        // add to bundle
        addResourceToBundle(bundle, report);

        return report;
    }

    private Observation mapObservation(Observation source) {
        var identifierType = new CodeableConcept().addCoding(
            new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("OBI"));
        var identifierValue = createIdHash(source.getIdentifierFirstRep());

        var obs = new Observation();
        // id
        obs.setId(identifierValue);
        // meta data
        obs.setMeta(new Meta().setProfile(List.of(new CanonicalType(
            "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab"))));

        // identifier
        obs.setIdentifier(List.of(new Identifier().setType(identifierType)
            .setSystem(fhirProperties.getSystems()
                .getObservationId())
            .setValue(identifierValue)
            .setAssigner(new Reference().setIdentifier(getIdentifierAssigner()))))

            // status
            .setStatus(source.getStatus())
            // category
            .setCategory(List.of(new CodeableConcept().addCoding(
                new Coding().setSystem("http://loinc.org")
                    .setCode("26436-6")).addCoding(
                new Coding()
                    .setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                    .setCode("laboratory"))
                // with local category
                .addCoding(source.getCategoryFirstRep().getCodingFirstRep())))

            // local coding: set system
            .setCode(new CodeableConcept().addCoding(source.getCode()
                .getCodingFirstRep().setSystem(fhirProperties.getSystems()
                    .getLaboratorySystem())))
            .setEffective(source.getEffective())
            .setValue(source.getValue())
            // interpretation
            // TODO validate
            .setInterpretation(
                source.getInterpretation().stream().map(
                    cc -> new CodeableConcept().setCoding(
                        // set system on each coding
                        cc.getCoding().stream()
                            .map(c -> c.setSystem(
                                "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation"))
                            .collect(
                                Collectors.toList()))).collect(Collectors.toList()))
            // map reference range to simple quantity with value only
            .setReferenceRange(
                source.getReferenceRange().stream()
                    .map(r -> r.setLow(new SimpleQuantity().setValue(r.getLow().getValue()))
                        .setHigh(new SimpleQuantity().setValue(r.getHigh().getValue()))).collect(
                    Collectors.toList()));

        return obs;
    }


    public MiiLabReportMapper mapServiceRequest(DiagnosticReport report, Bundle bundle) {
        var identifierType = new CodeableConcept(
            new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("PLAC"));
        var identifierValue = report.getIdentifierFirstRep().getValue();

        var serviceRequest = new ServiceRequest();
        // id
        serviceRequest.setId(identifierValue);
        // meta
        serviceRequest.getMeta().getProfile().add(new CanonicalType(
            "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ServiceRequestLab"));

        serviceRequest.setIdentifier(List.of(new Identifier().setSystem(fhirProperties.getSystems()
            .getServiceRequestId())
            .setType(identifierType)
            .setValue(identifierValue)
            .setAssigner(new Reference().setIdentifier(getIdentifierAssigner()))))

            // authoredOn
            // uses report effective (date)
            .setAuthoredOnElement(report.getEffectiveDateTimeType())

            // status & intent
            .setStatus(ServiceRequest.ServiceRequestStatus.COMPLETED)
            .setIntent(ServiceRequest.ServiceRequestIntent.ORDER)

            // category
            .setCategory(List.of(new CodeableConcept(
                new Coding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                    .setCode("laboratory"))))

            // code
            .setCode(new CodeableConcept()
                .setCoding(List.of(new Coding()
                    .setSystem("http://snomed.info/sct")
                    .setCode("59615004"))));

        // add to bundle
        addResourceToBundle(bundle, serviceRequest);
        return this;
    }


    /**
     * Set the encounter reference for {@link DiagnosticReport} and {@link Observation}
     */
    private void setEncounter(LaboratoryReport report, Bundle bundle) {
        if (report.getResource()
            .getEncounter()
            .getResource() == null) {
            throw new IllegalArgumentException(String.format(
                "Missing referenced encounter resource in report '%s'. Reference is '%s'",
                report.getId(), report.getResource()
                    .getEncounter()
                    .getReference()));
        }

        var encounterId = ((Encounter) report.getResource()
            .getEncounter()
            .getResource()).getIdentifierFirstRep()
            .getValue();

        getBundleEntryResources(bundle, ServiceRequest.class).forEach(r -> r.getEncounter()
            .setReference("Encounter/" + encounterId));
        getBundleEntryResources(bundle, DiagnosticReport.class).forEach(r -> r.getEncounter()
            .setReference("Encounter/" + encounterId));
        getBundleEntryResources(bundle, Observation.class).forEach(o -> o.getEncounter()
            .setReference("Encounter/" + encounterId));
    }

    /**
     * Set the subject reference for {@link DiagnosticReport} and {@link Observation}
     */
    public void setPatient(LaboratoryReport report, Bundle bundle) {
        if (report.getResource()
            .getSubject()
            .getResource() == null) {
            throw new IllegalArgumentException(String.format(
                "Missing referenced patient resource in report '%s'. Reference is '%s'",
                report.getId(), report.getResource()
                    .getSubject()
                    .getReference()));
        }

        var patientId = ((Patient) report.getResource()
            .getSubject()
            .getResource()).getIdentifierFirstRep()
            .getValue();

        getBundleEntryResources(bundle, ServiceRequest.class).forEach(r -> r.getSubject()
            .setReference("Patient/" + patientId));
        getBundleEntryResources(bundle, DiagnosticReport.class).forEach(r -> r.getSubject()
            .setReference("Patient/" + patientId));
        getBundleEntryResources(bundle, Observation.class).forEach(o -> o.getSubject()
            .setReference("Patient/" + patientId));

    }

    public <T> List<T> getBundleEntryResources(Bundle bundle, Class<T> domainType) {
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

    private String createIdHash(Identifier identifier) {
        return createIdHash(identifier.getSystem(), identifier.getValue());
    }

    private String createIdHash(String idSystem, String id) {
        return hasher.apply(idSystem + "|" + id).substring(0, 12);
    }

    private void addResourceToBundle(Bundle bundle, DomainResource resource) {
        var idElement = resource.getIdElement()
            .getValue();
        bundle.addEntry()
            .setFullUrl(resource.getResourceType()
                .name() + "/" + idElement)
            .setResource(resource);
    }

}

