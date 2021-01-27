package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.common.hash.Hashing;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.streams.kstream.ValueMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LabReportToFhirMapper implements ValueMapper<LaboratoryReport, Bundle> {

    private final static Logger log = LoggerFactory.getLogger(LabReportToFhirMapper.class);
    private final IParser fhirParser;
    private final FhirProperties fhirProperties;

    @Autowired
    public LabReportToFhirMapper(FhirContext fhirContext, FhirProperties fhirProperties) {
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
            mapPatient(report, bundle);
            mapEncounter(report, bundle);
            mapPerformer(report, bundle);
            mapServiceRequest(report, bundle);
            mapDiagnosticReport(report, bundle);

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


    private void mapDiagnosticReport(LaboratoryReport report, Bundle bundle) {

    }

    private void mapServiceRequest(LaboratoryReport report, Bundle bundle) {

    }

    private void mapPerformer(LaboratoryReport report, Bundle bundle) {

    }

    private void mapEncounter(LaboratoryReport report, Bundle bundle) {

    }

    // patient mapper
    public void mapPatient(LaboratoryReport report, Bundle bundle) {
        var source = (Patient) report.getResource()
            .getSubject()
            .getResource();

        var patient = new Patient();
        patient.setId(createIdHash(fhirProperties.getPatientIdSystem(),
            source.getIdentifierFirstRep()
                .getValue()));

        addResourceToBundle(bundle, patient);
        // ...
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

