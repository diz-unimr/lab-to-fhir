package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.hash.Hashing;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.hl7.fhir.r4.model.ServiceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MiiLabReportMapper implements ValueMapper<LaboratoryReport, Bundle> {

    private final static Logger log = LoggerFactory.getLogger(MiiLabReportMapper.class);
    private final IParser fhirParser;
    private final FhirProperties fhirProperties;
    private final Function<String, String> hasher = i -> Hashing.sha256()
        .hashString(i, StandardCharsets.UTF_8)
        .toString();
    private final Identifier identifierAssigner;

    @Autowired
    public MiiLabReportMapper(FhirContext fhirContext, FhirProperties fhirProperties) {
        this.fhirProperties = fhirProperties;
        fhirParser = fhirContext.newJsonParser();
        identifierAssigner = new Identifier().setSystem(fhirProperties.getAssignerIdSystem())
            .setValue(fhirProperties.getAssignerIdCode());
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
    public Bundle apply(LaboratoryReport report) {
        log.debug("Mapping LaboratoryReport: {}", report);
        Bundle bundle;

        try {
            bundle = map(report);
        } catch (Exception e) {
            log.error("Mapping failed for LaboratoryReport with id {} and order number {}",
                report.getId(), report.getReportIdentifierValue(), e);
            return null;
        }

        log.debug("Mapped successfully, fhir bundle: {}",
            fhirParser.encodeResourceToString(bundle));

        return bundle;
    }

    private Bundle map(LaboratoryReport report) {

        var bundle = new Bundle();
        try {

            // set meta information
            bundle.setId(report.getReportIdentifierValue());
            bundle.setType(BundleType.TRANSACTION);

            // TODO skip service request generation?
            mapServiceRequest(report, bundle);
            // TODO
            mapDiagnosticReport(report, bundle);

            setPatient(report, bundle);
            setEncounter(report, bundle);

            convertLoincUcum(bundle);

            return bundle;
        } catch (Exception e) {
            log.error("Error mapping LaboratoryReport with id {} and order number {}",
                report.getId(), bundle.getId(), e);
            throw e;
        }
    }

    // LOINC mapper
    public void convertLoincUcum(Bundle bundle) {

    }

    private void mapLoinc(Stream<Observation> observations) {

    }

    private MiiLabReportMapper mapDiagnosticReport(LaboratoryReport labReport, Bundle bundle) {
        var source = labReport.getResource();
        var identifierType = new CodeableConcept().addCoding(
            new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("FILL"));
        var report = new DiagnosticReport()

            // identifier
            .setIdentifier(List.of(new Identifier().setType(identifierType)
                .setSystem(fhirProperties.getDiagnosticReportSystem())
                .setValue(labReport.getResource()
                    .getIdentifierFirstRep()
                    .getValue())
                .setAssigner(new Reference().setIdentifier(getIdentifierAssigner()))))

            // basedOn
            .setBasedOn(
                List.of(new Reference("ServiceRequest/" + labReport.getReportIdentifierValue())))

            // status
            .setStatus(labReport.getResource()
                .getStatus())

            // category
            .setCategory(Stream.of(List.of(new CodeableConcept().addCoding(
                new Coding().setSystem("http://loinc.org")
                    .setCode("26436-6")), new CodeableConcept().addCoding(
                new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0074")
                    .setCode("LAB"))),
                // with local category
                labReport.getResource()
                    .getCategory())
                .flatMap(Collection::stream)
                .collect(Collectors.toList()))

            // code
            .setCode(source.getCode())

            // status
            .setStatus(source.getStatus())

            // effective
            .setEffective(source.getEffectiveDateTimeType())

            // effective
            .setIssuedElement(new InstantType(source.getEffectiveDateTimeType()))

            // result
            .setResult(mapObservations(source.getResult()).stream()
                .map(Reference::new)
                .collect(Collectors.toList()));

        // add to bundle
        addResourceToBundle(bundle, report);
        return this;
    }

    private List<Observation> mapObservations(List<Reference> result) {
        // TODO copy values

        // map LOINC
        mapLoinc(result.stream()
            .map(BaseReference::getResource)
            .map(Observation.class::cast));

        return List.of();
    }

    public void mapServiceRequest(LaboratoryReport report, Bundle bundle) {
        var serviceRequest = new ServiceRequest();

        // meta data
        var meta = new Meta();
        meta.setProfile(List.of(new CanonicalType(
            "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ServiceRequestLab")));
        serviceRequest.setMeta(meta);

        // identifier
        var identifierType = new CodeableConcept(
            new Coding().setSystem("http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("PLAC"));

        var identifier = new Identifier().setSystem(fhirProperties.getServiceRequestSystem())
            .setType(identifierType)
            .setValue(report.getResource()
                .getIdentifierFirstRep()
                .getValue())
            .setAssigner(new Reference().setIdentifier(getIdentifierAssigner()));
        serviceRequest.addIdentifier(identifier);

        // id
        serviceRequest.setId("ServiceRequest/" + identifier.getValue());

        // authoredOn
        // uses report effective (date)
        serviceRequest.setAuthoredOnElement(report.getResource()
            .getEffectiveDateTimeType());

        // status & intent
        serviceRequest.setStatus(ServiceRequest.ServiceRequestStatus.COMPLETED);
        serviceRequest.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);

        // category
        serviceRequest.setCategory(List.of(new CodeableConcept(
            new Coding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                .setCode("laboratory"))));

        // code
        serviceRequest.getCode()
            .addCoding()
            .setSystem("http://snomed.info/sct")
            .setCode("59615004");

        // add to bundle
        addResourceToBundle(bundle, serviceRequest);
    }


    /**
     * Set the encounter reference for {@link DiagnosticReport} and {@link Observation}
     */
    private void setEncounter(LaboratoryReport report, Bundle bundle) {
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
        return hasher.apply(idSystem + "|" + id);
    }

    private void addResourceToBundle(Bundle bundle, DomainResource resource) {
        if (fhirProperties.getGenerateNarrative()) {
            setNarrative(resource);
        }

        var idElement = resource.getIdElement()
            .getValue();
        bundle.addEntry()
            .setFullUrl(resource.getResourceType()
                .name() + "/" + idElement)
            .setResource(resource);
    }

}

