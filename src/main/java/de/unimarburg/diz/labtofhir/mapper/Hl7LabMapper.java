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
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.codesystems.V3ObservationInterpretation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "mapping", name = "hl7.enabled",
    matchIfMissing = true)
public class Hl7LabMapper extends BaseMapper<ORU_R01> {

    private final Map<String, String> categoryMap;

    Hl7LabMapper(FhirContext fhirContext, FhirProperties fhirProperties,
                 @Qualifier("categoryMap") Map<String, String> categoryMap) {
        super(fhirContext, fhirProperties);
        this.categoryMap = categoryMap;
    }

    @Override
    String getMapperName() {
        return "hl7-lab-mapper";
    }

    @Override
    public Bundle apply(ORU_R01 msg) {

        var msgId = msg.getMSH()
            .getMsh10_MessageControlID().getValue();

        var orderId = getOrderNumber(msg);

        log.debug("Mapping HL7 message with id:{} and order number:{}", msgId,
            orderId);

        var bundle = new Bundle();
        try {

            // set meta information (with valid id)
            bundle.setId(msgId);
            bundle.setType(Bundle.BundleType.TRANSACTION);

            var request = mapServiceRequest(msg);
            var report = mapDiagnosticReport(msg);
            var obs = mapObservations(msg);

            addResourceToBundle(bundle, request);
            addResourceToBundle(bundle, report);
            obs.forEach(o -> addResourceToBundle(bundle, o));

            mapPatient(msg, bundle);
            mapEncounter(msg, bundle);

        } catch (Exception e) {
            log.error("Mapping failed for HL7 message with id:{} and order "
                + "number:{}", msgId, orderId);

            return null;
        }

        log.debug(
            "Mapped successfully to FHIR bundle: [id={}], [order number={}]",
            msgId, orderId);
        log.trace("FHIR bundle: {}",
            fhirParser().encodeResourceToString(bundle));

        return bundle;
    }

    private List<Observation> mapObservations(ORU_R01 msg) throws HL7Exception {

        var identifierType = new CodeableConcept().addCoding(
            new Coding().setSystem(
                    "http://terminology.hl7.org/CodeSystem/v2-0203")
                .setCode("OBI"));

        var result = new ArrayList<Observation>();

        var orderObs = msg.getPATIENT_RESULT().getORDER_OBSERVATIONAll();

        for (ORU_R01_ORDER_OBSERVATION order : orderObs) {

            var obr = order.getOBR();
            var obx = order.getOBSERVATION().getOBX();
            var nte = order.getOBSERVATION().getNTE();

            var code =
                obx.getObservationIdentifier().getCe1_Identifier().getValue();

            var effective =
                new DateTimeType(obr.getObservationDateTime().getTimeOfAnEvent()
                    .getValueAsDate());

            // identifier
            var identifierValue =
                createObsId(msg.getMSH().getSendingApplication().getValue(),
                    getRequestNumber(msg), code, effective);

            var obs = new Observation();
            // id
            obs.setId(identifierValue);
            // meta data
            obs.setMeta(getMeta(ResourceType.Observation.name()));

            // identifier
            obs.addIdentifier(new Identifier().setType(identifierType)
                    .setSystem(fhirProperties().getSystems()
                        .getObservationId())
                    .setValue(identifierValue)
                    .setAssigner(new Reference()
                        .setIdentifier(identifierAssigner())))

                // status
                .setStatus(parseObservationStatus(obx))


                // category
                .setCategory(List.of(new CodeableConcept().addCoding(
                        new Coding().setSystem("http://loinc.org")
                            .setCode("26436-6"))
                    .addCoding(new Coding()
                        .setSystem("http://terminology.hl7"
                            + ".org/CodeSystem/observation-category")
                        .setCode("laboratory"))
                    // with local category
                    .addCoding(mapReportCategory(code))
                ))

                // code
                .setCode(new CodeableConcept()
                    .addCoding(
                        new Coding(fhirProperties().getSystems()
                            .getLaboratorySystem(),
                            code,
                            obx.getObservationIdentifier().getCe2_Text()
                                .getValue())))
                // effective
                .setEffective(effective)
                // value
                .setValue(parseValue(obx))
                // interpretation
                .setInterpretation(parseInterpretation(obx))
                // map reference range to simple quantity with value only
                .setReferenceRange(parseReferenceRange(obx, obs.getValue()))

                // note
                .setNote(Arrays.stream(nte.getComment())
                    .map(n -> new Annotation().setText(n.getValue())).toList());

            result.add(obs);

        }

        return result;
    }

    @SuppressWarnings("checkstyle:LineLength")
    private List<Observation.ObservationReferenceRangeComponent> parseReferenceRange(
        OBX obx, Type value) {

        var rangeString = obx.getReferencesRange().getValue();
        if (rangeString == null) {
            return List.of();
        }

        var parts = rangeString.split(" - ", 2);

        if (parts.length == 2 && value instanceof Quantity q) {
            // set code and system according to its quantity value
            var low = new SimpleQuantity().setCode(q.getCode())
                .setSystem(q.getSystem())
                .setValue(NumberUtils.createDouble(parts[0]));

            var high = new SimpleQuantity().setCode(q.getCode())
                .setSystem(q.getSystem())
                .setValue(NumberUtils.createDouble(parts[0]));

            return List.of(
                new Observation.ObservationReferenceRangeComponent()
                    .setLow(low)
                    .setHigh(high));
        }

        return List.of(
            new Observation.ObservationReferenceRangeComponent().setText(
                parts[0]));
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
                    .setUnit(unit);
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
                            String.valueOf(comp))).setUnit(unit);
                }

                yield new StringType(valueString);
            }

            default -> null;
        };
    }

    private String createObsId(String sendingApplication, String requestNumber,
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
        throws DataTypeException {
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
                        .getServiceRequestId()).setValue(reportId)
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
        throws DataTypeException {
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

    private ServiceRequest.ServiceRequestStatus parseOrderStatus(ORU_R01 msg) {
        // see also
        // https://smilecdr.com/docs/hl7_v2x_support/table_definitions.html#0038


        return switch (msg.getPATIENT_RESULT().getORDER_OBSERVATION().getORC()
            .getOrderStatus().getValue()) {
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
        ORU_R01 msg) {
        // see also
        // https://smilecdr.com/docs/hl7_v2x_support/table_definitions.html#0123

        return switch (msg.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR()
            .getResultStatus().getValue()) {
            case "O" -> DiagnosticReport.DiagnosticReportStatus.REGISTERED;
            case "P", "I" -> DiagnosticReport.DiagnosticReportStatus.PARTIAL;
            case "F" -> DiagnosticReport.DiagnosticReportStatus.FINAL;
            case "C" -> DiagnosticReport.DiagnosticReportStatus.CORRECTED;
            case "X" -> DiagnosticReport.DiagnosticReportStatus.CANCELLED;

            default -> DiagnosticReport.DiagnosticReportStatus.UNKNOWN;
        };
    }

    private Observation.ObservationStatus parseObservationStatus(
        OBX obx) {
        // see also
        // https://smilecdr.com/docs/hl7_v2x_support/table_definitions.html#0085

        return switch (obx.getObservationResultStatus().getValue()) {
            case "O" -> Observation.ObservationStatus.REGISTERED;
            case "P", "I" -> Observation.ObservationStatus.PRELIMINARY;
            case "F" -> Observation.ObservationStatus.FINAL;
            case "C" -> Observation.ObservationStatus.CORRECTED;
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
}
