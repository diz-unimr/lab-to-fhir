package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.hash.Hashing;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Encounter;
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

    @Autowired
    public MiiLabReportMapper(FhirContext fhirContext, FhirProperties fhirProperties) {
        this.fhirProperties = fhirProperties;
        fhirParser = fhirContext.newJsonParser();
    }

    @Override
    public Bundle apply(LaboratoryReport report) {
        log.debug("Mapping LaboratoryReport: {}", report);
        Bundle bundle;

        try {
            bundle = map(report);
        } catch (Exception e) {
            log.error("Mapping failed for LaboratoryReport with id {} and order number {}",
                report.getId(), report.getResource()
                    .getIdentifierFirstRep()
                    .getValue(), e);
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
            bundle.setId(report.getResource()
                .getIdentifierFirstRep()
                .getValue());
            bundle.setType(BundleType.TRANSACTION);

            // TODO
            mapServiceRequest(report, bundle);
            // TODO
            mapDiagnosticReport(report, bundle);

            setPatient(report, bundle);
            setEncounter(report, bundle);

            mapLoinc(bundle);
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

    private void mapLoinc(Bundle bundle) {

    }

    private void mapDiagnosticReport(LaboratoryReport labReport, Bundle bundle) {
        var source = labReport.getResource();
        var report = new DiagnosticReport();

        // code
        report.setCode(source.getCode());

        // category
        // TODO improve category by system and code
        report.setCategory(report.getCategory());

        // status
        report.setStatus(source.getStatus());

        // result
        report.setResult(mapObservations(source.getResult()));

        // text
        setNarrative(report);
        // add to bundle
        addResourceToBundle(bundle, report);
    }

    private List<Reference> mapObservations(List<Reference> result) {
        // copy values
        mapLoinc(obs);
        return null;
    }

    private void setNarrative(DomainResource resource) {
        var narrative = fhirParser.setPrettyPrint(true)
            .encodeResourceToString(resource);
        resource.getText()
            .setStatus(NarrativeStatus.GENERATED);
        resource.getText()
            .setDivAsString(narrative);
    }

    private void mapServiceRequest(LaboratoryReport report, Bundle bundle) {
        var serviceRequest = new ServiceRequest();
        // TODO implement

        // text
        setNarrative(serviceRequest);

        // add to bundle
        addResourceToBundle(bundle, serviceRequest);
    }


    /**
     * Set the encounter reference for {@link DiagnosticReport} and {@link Observation}
     */
    private void setEncounter(LaboratoryReport report, Bundle bundle) {
        var encounterId = createIdHash(fhirProperties.getPatientIdSystem(),
            ((Encounter) report.getResource()
                .getSubject()
                .getResource()).getIdentifierFirstRep()
                .getValue());

        getBundleEntryResources(bundle, DiagnosticReport.class).forEach(r -> r.getEncounter()
            .setReference("Encounter/" + encounterId));
        getBundleEntryResources(bundle, Observation.class).forEach(o -> o.getEncounter()
            .setReference("Encounter/" + encounterId));
    }

    /**
     * Set the subject reference for {@link DiagnosticReport} and {@link Observation}
     */
    public void setPatient(LaboratoryReport report, Bundle bundle) {
        var patientId = createIdHash(fhirProperties.getPatientIdSystem(),
            ((Patient) report.getResource()
                .getSubject()
                .getResource()).getIdentifierFirstRep()
                .getValue());

        getBundleEntryResources(bundle, DiagnosticReport.class).forEach(r -> r.getSubject()
            .setReference("Patient/" + patientId));
        getBundleEntryResources(bundle, Observation.class).forEach(o -> o.getSubject()
            .setReference("Patient/" + patientId));
    }

    public <T> List<T> getBundleEntryResources(Bundle bundle, Class<T> domainType) {
        return bundle.getEntry()
            .stream()
            .map(domainType::isInstance)
            .map(domainType::cast)
            .collect(Collectors.toList());
    }


    private String createIdHash(String idSystem, String id) {
        return Hashing.sha256()
            .hashString(idSystem + "|" + id, StandardCharsets.UTF_8)
            .toString();
    }

    private Bundle addResourceToBundle(Bundle bundle, DomainResource resource) {
        var idElement = resource.getIdElement()
            .getValue();
        bundle.addEntry()
            .setFullUrl(resource.getResourceType()
                .name() + "/" + idElement)
            .setResource(resource);
        return bundle;
    }

}

