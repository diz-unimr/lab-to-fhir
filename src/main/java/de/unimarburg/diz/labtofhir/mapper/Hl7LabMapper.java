package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.v22.datatype.NM;
import ca.uhn.hl7v2.model.v22.datatype.ST;
import ca.uhn.hl7v2.model.v22.group.ORU_R01_ORDER_OBSERVATION;
import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import ca.uhn.hl7v2.model.v22.segment.OBX;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.codesystems.V3ObservationInterpretation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "mapping", name = "hl7.enabled",
    matchIfMissing = true)
public class Hl7LabMapper extends BaseMapper<ORU_R01> {

    private static final int OBX_OBSERVATION_VALUE_SEGMENT = 5;
    private final Map<String, String> categoryMap;

    Hl7LabMapper(FhirContext fhirContext, FhirProperties fhirProperties,
                 @Autowired(required = false) @Qualifier("hl7Filter")
                 DateFilter filter,
                 @Qualifier("categoryMap") Map<String, String> categoryMap) {
        super(fhirContext, fhirProperties, filter);
        this.categoryMap = categoryMap;
    }

    @Override
    String getMapperName() {
        return "hl7-lab-mapper";
    }

    @Override
    protected Predicate<ORU_R01> createFilter(DateFilter filter) {

        return msg -> {
            try {
                return createDateTimeFilter(
                    new DateTimeType(getOrderDate(msg)), filter);
            } catch (DataTypeException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Override
    Bundle map(ORU_R01 msg) {

        var msgId = msg.getMSH()
            .getMsh10_MessageControlID().getValue();

        var orderId = getOrderNumber(msg);

        var bundle = new Bundle();
        try {

            // set meta information (with valid id)
            bundle.setId(sanitizeId(msgId));
            bundle.setType(Bundle.BundleType.TRANSACTION);

            var request = mapServiceRequest(msg);
            var report = mapDiagnosticReport(msg);
            var obs = mapObservations(msg);

            // set report result
            report.setResult(
                obs.stream().map(this::getObservationsReference).toList());

            // add resources to bundle
            addResourceToBundle(bundle, request,
                request.getIdentifierFirstRep());
            addResourceToBundle(bundle, report,
                report.getIdentifierFirstRep());
            obs.forEach(
                o -> addResourceToBundle(bundle, o,
                    o.getIdentifierFirstRep()));

            mapPatient(msg, bundle);
            mapEncounter(msg, bundle);

        } catch (HL7Exception | RuntimeException e) {
            log.error(
                "Mapping failed for HL7 message with [id={}], "
                    + "[order-number={}]", msgId, orderId, e);

            return null;
        }

        log.debug(
            "Mapped successfully to FHIR bundle: [id={}], [order-number={}]",
            msgId, orderId);
        log.trace("FHIR bundle: {}",
            fhirParser().encodeResourceToString(bundle));

        return bundle;
    }

    private List<Observation> mapObservations(ORU_R01 msg) throws
        HL7Exception {

        var result = new ArrayList<Observation>();

        var orderObs = msg.getPATIENT_RESULT().getORDER_OBSERVATIONAll();

        for (ORU_R01_ORDER_OBSERVATION order : orderObs) {

            var obr = order.getOBR();
            var obx = order.getOBSERVATION().getOBX();
            var nte = order.getOBSERVATION().getNTE();

            // code
            var code =
                obx.getObservationIdentifier().getCe1_Identifier()
                    .getValue();
            var codeCoding = new CodeableConcept()
                .addCoding(
                    new Coding(fhirProperties().getSystems()
                        .getLaboratorySystem(),
                        code,
                        obx.getObservationIdentifier().getCe2_Text()
                            .getValue()));

            var effective =
                new DateTimeType(
                    obr.getObservationDateTime().getTimeOfAnEvent()
                        .getValueAsDate());

            // identifier
            var identifierValue =
                createObsId(msg.getMSH().getSendingApplication().getValue(),
                    getRequestNumber(msg), code, effective);

            var obs = createObservation(identifierValue);

            // status
            obs.setStatus(parseObservationStatus(obx))

                // category
                .setCategory(List.of(getObservationCategory()
                    // with local category
                    .addCoding(mapReportCategory(code))
                ))

                // code
                .setCode(codeCoding)
                // effective
                .setEffective(effective)
                // value
                .setValue(parseValue(obx))
                // secondary value in component
                .setComponent(parseRepeatingValue(obx, codeCoding))
                // interpretation
                .setInterpretation(parseInterpretation(obx))
                // map reference range to simple quantity with value only
                .setReferenceRange(Optional.ofNullable(
                    parseReferenceRange(obx.getReferencesRange().getValue(),
                        obs.getValue())).map(List::of).orElse(null))

                // note
                .setNote(Arrays.stream(nte.getComment())
                    .map(n -> new Annotation().setText(n.getValue()))
                    .toList());

            result.add(obs);

        }

        return result;
    }

    private List<CodeableConcept> parseInterpretation(OBX obx) {

        return Arrays.stream(obx.getAbnormalFlags()).map(flag -> {
                var interpretationCode = V3ObservationInterpretation.fromCode(
                    flag.getValue());

                return new CodeableConcept().addCoding(new Coding(
                    "http://terminology.hl7.org/CodeSystem/v3"
                        + "-ObservationInterpretation",
                    interpretationCode.toCode(),
                    interpretationCode.getDisplay()));
            }
        ).toList();
    }

    /*
        Parse repeating OBX-5 values. This is not part of HL7 v2.2 but used
        anyway, so we are mapping those values to Observation.component
     */
    private List<Observation.ObservationComponentComponent>
    parseRepeatingValue(
        OBX obx, CodeableConcept code) {

        var reps = getObservationValueReps(obx);
        if (reps == null) {
            return null;
        }

        var components =
            new ArrayList<Observation.ObservationComponentComponent>();

        for (var rep : reps) {
            // parse value
            Type valueType;
            if (NumberUtils.isCreatable(rep)) {
                valueType = new Quantity(NumberUtils.createDouble(rep));
            } else {
                valueType = new StringType(rep);
            }

            components.add(
                new Observation.ObservationComponentComponent(
                    code).setValue(
                    valueType));
        }

        return components;
    }

    private Type parseValue(OBX obx) throws HL7Exception {

        var valueType = obx.getValueType();
        var value = obx.getObservationValue();
        var unit = obx.getUnits().getCe1_Identifier().getValue();

        if (value.isEmpty()) {
            return null;
        }

        return switch (valueType.getValue()) {
            case "NM" -> {
                var valueNumeric = (NM) value.getData();
                yield new Quantity(
                    NumberUtils.createDouble(valueNumeric.getValue()))
                    .setUnit(unit).setSystem(fhirProperties().getSystems()
                        .getLaboratoryUnitSystem()).setCode(unit);
            }
            case "ST" -> {
                var valueString = ((ST) value.getData()).getValue();

                // check first character for comparator value
                var comp = valueString.charAt(0);
                var valuePart = valueString.substring(1)
                    .trim();

                if ((comp == '<' || comp == '>') && NumberUtils.isCreatable(
                    valuePart)) {
                    yield new Quantity(
                        NumberUtils.createDouble(valuePart)).setComparator(
                            Quantity.QuantityComparator.fromCode(
                                String.valueOf(comp))).setUnit(unit)
                        .setSystem(fhirProperties().getSystems()
                            .getLaboratoryUnitSystem()).setCode(unit);
                }

                yield new StringType(valueString);
            }

            default -> null;
        };
    }

    private String createObsId(String sendingApplication, String
        requestNumber,
                               String code, DateTimeType effective) {
        // conforms to id generation from Synedra AIM FHIR mapping
        // i.e. msg.get('MSH.3')+'_'+msg.get('OBR.2') + "_" + msg.get("OBX.3.1")
        return createId(null, String.format("%s_%s_%s", sendingApplication,
            requestNumber, code), effective);
    }

    private void mapEncounter(ORU_R01 msg, Bundle bundle) {
        var encounterId =
            msg.getPATIENT_RESULT().getPATIENT().getPV1()
                .getVisitNumber().getIDNumber().getValue();

        setEncounter(encounterId, bundle);
    }

    private void mapPatient(ORU_R01 msg, Bundle bundle) {
        var patientId =
            msg.getPATIENT_RESULT().getPATIENT().getPID()
                .getPatientIDInternalID(0).getCm_pat_id1_IDNumber()
                .getValue();

        setPatient(patientId, bundle);
    }

    private DiagnosticReport mapDiagnosticReport(ORU_R01 msg)
        throws HL7Exception {
        // id
        var reportId = getOrderNumber(msg);

        var report = new DiagnosticReport();

        // id and meta data
        report.setId(sanitizeId(reportId))
            .setMeta(getMeta(ResourceType.DiagnosticReport.name()));
        report.addIdentifier(
                new Identifier().setType(new CodeableConcept().setCoding(
                        List.of(new Coding().setSystem(
                                "http://terminology.hl7.org/CodeSystem/v2-0203")
                            .setCode("FILL"))))
                    .setSystem(fhirProperties().getSystems()
                        .getDiagnosticReportId()).setValue(reportId)
                    .setAssigner(
                        new Reference().setIdentifier(identifierAssigner())))

            // basedOn
            .setBasedOn(List.of(new Reference(
                getConditionalReference(ResourceType.ServiceRequest,
                    getRequestNumber(msg)))))

            // status
            .setStatus(parseResultStatus(msg))

            // category
            .setCategory(getReportCategory())

            // code
            .setCode(new CodeableConcept(new Coding("http://loinc.org",
                "11502-2", "Laboratory report")).setText(
                "Laboratory report"))

            // effective
            .setEffective(new DateTimeType(getOrderDate(msg)))

            // issued
            .setIssuedElement(
                new InstantType(getOrderDate(msg)));

        return report;
    }

    private Date getOrderDate(ORU_R01 msg) throws DataTypeException {
        return msg.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR()
            .getObservationDateTime()
            .getTimeOfAnEvent()
            .getValueAsDate();
    }

    private Coding mapReportCategory(String code) {
        var cat = categoryMap.get(code);
        return cat != null ? new Coding().setDisplay(cat) : null;
    }

    private ServiceRequest mapServiceRequest(ORU_R01 msg)
        throws HL7Exception {
        // id
        var requestId = getRequestNumber(msg);

        var request = new ServiceRequest();

        // id and meta data
        request.setId(sanitizeId(requestId))
            .setMeta(getMeta(ResourceType.ServiceRequest.name()));
        request.addIdentifier(
                new Identifier().setType(new CodeableConcept().setCoding(
                        List.of(new Coding().setSystem(
                                "http://terminology.hl7.org/CodeSystem/v2-0203")
                            .setCode("PLAC"))))
                    .setSystem(fhirProperties().getSystems()
                        .getServiceRequestId()).setValue(requestId)
                    .setAssigner(
                        new Reference().setIdentifier(identifierAssigner())))


            // authoredOn (ORC-9)
            .setAuthoredOnElement(new DateTimeType(
                msg.getPATIENT_RESULT().getORDER_OBSERVATION().getORC()
                    .getDateTimeOfTransaction().getTimeOfAnEvent()
                    .getValueAsDate()))

            // status & intent
            .setStatus(parseOrderStatus(msg))
            .setIntent(ServiceRequest.ServiceRequestIntent.ORDER)

            // category
            .setCategory(getServiceRequestCategory())

            // code
            .setCode(getServiceRequestCode());

        return request;
    }

    private ServiceRequest.ServiceRequestStatus parseOrderStatus(ORU_R01
                                                                     msg)
        throws HL7Exception {

        var status = msg.getPATIENT_RESULT().getORDER_OBSERVATION()
            .getORC()
            .getOrderStatus();
        if (status.isEmpty()) {
            return ServiceRequest.ServiceRequestStatus.UNKNOWN;
        }

        return switch (status.getValue()) {
            case "A", "IP" -> ServiceRequest.ServiceRequestStatus.ACTIVE;
            case "CM", "DC" -> ServiceRequest.ServiceRequestStatus.COMPLETED;
            case "HD" -> ServiceRequest.ServiceRequestStatus.ONHOLD;
            case "SC" -> ServiceRequest.ServiceRequestStatus.DRAFT;
            case "CA", "RP", "ER" ->
                ServiceRequest.ServiceRequestStatus.REVOKED;
            default -> ServiceRequest.ServiceRequestStatus.UNKNOWN;
        };
    }

    private DiagnosticReport.DiagnosticReportStatus parseResultStatus(
        ORU_R01 msg) throws HL7Exception {

        var status = msg.getPATIENT_RESULT().getORDER_OBSERVATION()
            .getOBR()
            .getResultStatus();
        if (status.isEmpty()) {
            return DiagnosticReport.DiagnosticReportStatus.UNKNOWN;
        }

        return switch (status.getValue()) {
            case "O", "I", "S" ->
                DiagnosticReport.DiagnosticReportStatus.REGISTERED;
            case "P" -> DiagnosticReport.DiagnosticReportStatus.PRELIMINARY;
            case "F" -> DiagnosticReport.DiagnosticReportStatus.FINAL;
            case "C" -> DiagnosticReport.DiagnosticReportStatus.CORRECTED;
            case "R" -> DiagnosticReport.DiagnosticReportStatus.PARTIAL;
            case "X" -> DiagnosticReport.DiagnosticReportStatus.CANCELLED;

            default -> DiagnosticReport.DiagnosticReportStatus.UNKNOWN;
        };
    }

    @SuppressWarnings("checkstyle:LineLength")
    private Observation.ObservationStatus parseObservationStatus(
        OBX obx) throws HL7Exception {

        if (obx.getObservationResultStatus().isEmpty()) {
            return Observation.ObservationStatus.UNKNOWN;
        }

        return switch (obx.getObservationResultStatus().getValue()) {

            case "C" -> Observation.ObservationStatus.CORRECTED;
            case "D" -> Observation.ObservationStatus.ENTEREDINERROR;
            case "F", "U" -> Observation.ObservationStatus.FINAL;
            case "I" -> Observation.ObservationStatus.REGISTERED;
            case "P", "R" -> Observation.ObservationStatus.PRELIMINARY;
            case "X" -> Observation.ObservationStatus.CANCELLED;

            default -> Observation.ObservationStatus.UNKNOWN;
        };
    }

    private String getRequestNumber(ORU_R01 msg) {
        return msg.getPATIENT_RESULT()
            .getORDER_OBSERVATION()
            .getOBR()
            .getPlacerOrderNumber().getUniquePlacerId().getValue();
    }

    private String getOrderNumber(ORU_R01 msg) {
        var orderId = msg.getPATIENT_RESULT()
            .getORDER_OBSERVATION()
            .getORC().getFillerOrderNumber().getUniqueFillerId();

        // Filler Order Number seems to be empty
        return Optional.ofNullable(orderId.getValue())
            .orElse(getRequestNumber(msg));
    }

    List<String> getObservationValueReps(OBX obx) {

        try {
            if (obx.getField(OBX_OBSERVATION_VALUE_SEGMENT).length < 2) {
                // only repeated values are handled here
                return null;
            }

            var result = new ArrayList<String>();

            var obx5 = obx.getField(OBX_OBSERVATION_VALUE_SEGMENT);
            for (int i = 1; i < obx5.length; i++) {
                result.add(obx5[i].encode());
            }
            return result;

        } catch (HL7Exception e) {
            return null;
        }
    }
}
