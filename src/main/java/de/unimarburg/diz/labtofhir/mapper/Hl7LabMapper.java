package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "mapping", name = "hl7.enabled",
    matchIfMissing = true)
public class Hl7LabMapper extends BaseMapper<ORU_R01> {

    Hl7LabMapper(FhirContext fhirContext, FhirProperties fhirProperties,
                 LoincMapper loincMapper) {
        super(fhirContext, fhirProperties, loincMapper);
    }


    @Override
    public Bundle apply(ORU_R01 msg) {

        var msgId = msg.getMSH()
            .getMsh10_MessageControlID().getValue();
        // TODO is filler always empty? should we use placer instead?
        var orderId = msg.getPATIENT_RESULT()
            .getORDER_OBSERVATION()
            .getOBR()
            .getObr3_FillerOrderNumber().getUniqueFillerId().getValue();

        log.debug("Mapping HL7 message with id:{} and order number:{}", msgId,
            orderId);

        var bundle = new Bundle();
        try {

            // set meta information (with valid id)
            bundle.setId(msgId);
            bundle.setType(Bundle.BundleType.TRANSACTION);

            var request = createServiceRequest(msg);


        } catch (Exception e) {
            log.error("Mapping failed for HL7 message with id:{} and order "
                + "number:{}", msgId, orderId);

            return null;
        }

        log.debug("Mapped successfully to FHIR bundle: id:{}, order number:{}",
            msgId, orderId);
        log.trace("FHIR bundle: {}",
            fhirParser().encodeResourceToString(bundle));

        return bundle;
    }

    private ServiceRequest createServiceRequest(ORU_R01 msg)
        throws DataTypeException {
        // id
        var requestId = msg.getPATIENT_RESULT()
            .getORDER_OBSERVATION()
            .getOBR()
            .getObr2_PlacerOrderNumber()
            .getCm_placer1_UniquePlacerId().getValue();

        var request = new ServiceRequest();

        // id and meta data
        request.setId(requestId)
            .setMeta(new Meta().addProfile(
                    "https://www.medizininformatik-initiative"
                        + ".de/fhir/core/modul-labor/StructureDefinition"
                        + "/ServiceRequestLab")
                .setSource("#swisslab-hl7"));
        request.addIdentifier(
                new Identifier().setType(new CodeableConcept().setCoding(
                        List.of(new Coding().setSystem(
                                "http://terminology.hl7.org/CodeSystem/v2-0203")
                            .setCode("PLAC"))))
                    .setSystem(fhirProperties().getSystems()
                        .getServiceRequestId()).setValue(requestId)
                    .setAssigner(
                        new Reference().setIdentifier(identifierAssigner())))


            // authoredOn
            // TODO OBR-6 or 21?
            // TODO check conversion
            .setAuthoredOnElement(new DateTimeType(
                msg.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR()
                    .getObr6_RequestedDateTimeNotused().getTs1_TimeOfAnEvent()
                    .getValueAsDate()))

            // status & intent
            // TODO status mapping
            .setStatus(ServiceRequest.ServiceRequestStatus.fromCode(mapStatus(
                msg.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR()
                    .getObr25_ResultStatus().getValue())))
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

        return request;
    }

    private String mapStatus(String value) {
        // TODO implement
        return null;
    }
}
