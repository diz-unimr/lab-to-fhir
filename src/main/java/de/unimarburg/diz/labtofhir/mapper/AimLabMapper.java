package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.LaboratoryReport;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Observation.ObservationReferenceRangeComponent;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "mapping", name = "aim.enabled",
    matchIfMissing = true)
public class AimLabMapper extends BaseMapper<LaboratoryReport> {

    public AimLabMapper(FhirContext fhirContext,
                        FhirProperties fhirProperties,
                        @Autowired(required = false) @Qualifier("aimFilter")
                        DateFilter filter) {
        super(fhirContext, fhirProperties, filter);
    }

    @Override
    String getMapperName() {
        return "aim-lab-mapper";
    }

    @Override
    protected Predicate<LaboratoryReport> createFilter(DateFilter filter) {

        return r -> createDateTimeFilter(
            r.getResource().getEffectiveDateTimeType(), filter);
    }

    @Override
    Bundle map(LaboratoryReport report) {

        Bundle bundle = new Bundle();
        try {

            // set meta information (with valid id)
            bundle.setId(sanitizeId(report.getReportIdentifierValue()));
            bundle.setType(BundleType.TRANSACTION);


            // service request
            mapServiceRequest(report.getResource(), bundle);

            // diagnostic report
            var mappedReport = mapDiagnosticReport(report.getResource(), bundle)

                // observations
                .setResult((report.getObservations()
                    .stream()
                    // map observations
                    .map(this::mapObservation)

                    // add to bundle
                    .peek(o -> addResourceToBundle(bundle, o,
                        o.getIdentifierFirstRep()))
                    // set result references
                    .map(o -> new Reference(
                        getConditionalReference(ResourceType.Observation,
                            o.getId())))).collect(Collectors.toList()));

            if (mappedReport.getResult()
                .isEmpty()) {
                // report contains no observations
                log.info(
                    "No Observations after mapping for LaboratoryReport with "
                        + "[id={}] and [order-number={}]. Discarding.",
                    report.getId(), report.getReportIdentifierValue());
                return null;
            }

            setPatient(report, bundle);
            setEncounter(report, bundle);
        } catch (Exception e) {
            log.error(
                "Mapping failed for LaboratoryReport with [id={}], "
                    + "[order-number={}]", report.getId(),
                report.getReportIdentifierValue(), e);
            // TODO add metrics
            return null;
        }

        log.debug(
            "Mapped successfully to FHIR bundle: [id={}], [order-number={}]",
            report.getId(), report.getReportIdentifierValue());
        log.trace("FHIR bundle: {}",
            fhirParser().encodeResourceToString(bundle));

        return bundle;
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
        report.setId(sanitizeId(identifierValue));
        // meta data
        report.setMeta(getMeta(ResourceType.DiagnosticReport.name()));

        // identifier
        report.setIdentifier(List.of(new Identifier().setType(identifierType)
                .setSystem(fhirProperties().getSystems()
                    .getDiagnosticReportId())
                .setValue(identifierValue)
                .setAssigner(
                    new Reference().setIdentifier(identifierAssigner()))))

            // basedOn
            .setBasedOn(List.of(new Reference(
                getConditionalReference(ResourceType.ServiceRequest,
                    identifierValue))))

            // status
            .setStatus(labReport.getStatus())

            // category
            .setCategory(getReportCategory())

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
        addResourceToBundle(bundle, report, report.getIdentifierFirstRep());

        return report;
    }

    private Observation mapObservation(Observation source) {

        var identifierValue = createId(source.getIdentifierFirstRep(),
            source.getEffectiveDateTimeType());

        var obs = createObservation(identifierValue);

        // status
        obs.setStatus(source.getStatus())

            // category
            .setCategory(List.of(getObservationCategory()
                // with local category
                .addCoding(source.getCategoryFirstRep()
                    .getCodingFirstRep())))

            // local coding: set system
            .setCode(new CodeableConcept().addCoding(source.getCode()
                .getCodingFirstRep()
                .setSystem(fhirProperties().getSystems()
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

            var quantity = parseQuantity(valueString);
            if (quantity != null) {
                return quantity;
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
                .setSystem(fhirProperties().getSystems()
                    .getLaboratoryUnitSystem());
        }
    }


    public void mapServiceRequest(DiagnosticReport report,
                                  Bundle bundle) {
        var identifierType = new CodeableConcept(new Coding().setSystem(
                "http://terminology.hl7.org/CodeSystem/v2-0203")
            .setCode("PLAC"));
        var identifierValue = report.getIdentifierFirstRep()
            .getValue();

        var serviceRequest = new ServiceRequest();
        // id
        serviceRequest.setId(sanitizeId(identifierValue));
        // meta
        serviceRequest.setMeta(getMeta(ResourceType.ServiceRequest.name()));

        serviceRequest.setIdentifier(List.of(new Identifier().setSystem(
                    fhirProperties().getSystems()
                        .getServiceRequestId())
                .setType(identifierType)
                .setValue(identifierValue)
                .setAssigner(
                    new Reference().setIdentifier(identifierAssigner()))))

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
        addResourceToBundle(bundle, serviceRequest,
            serviceRequest.getIdentifierFirstRep());
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

        setEncounter(encounterId, bundle);
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

        setPatient(patientId, bundle);
    }
}

