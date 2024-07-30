package de.unimarburg.diz.labtofhir.mapper;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.hl7v2.model.v22.message.ORU_R01;
import de.unimarburg.diz.labtofhir.configuration.FhirProperties;
import de.unimarburg.diz.labtofhir.model.SupportedDataFormat;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "mapping", name = "mode", havingValue = SupportedDataFormat.HL7)
public class Hl7LabMapper extends BaseMapper<ORU_R01> {

    Hl7LabMapper(FhirContext fhirContext, FhirProperties fhirProperties,
        LoincMapper loincMapper) {
        super(fhirContext, fhirProperties, loincMapper);
    }


    @Override
    public Bundle apply(ORU_R01 msg) {
        log.debug("Mapping HL7 message with id:{} and order number:{}",
            msg.getMSH().getMsh10_MessageControlID(),
            msg.getPATIENT_RESULT().getORDER_OBSERVATION().getOBR()
                .getObr2_PlacerOrderNumber());

        //        if (msg instanceof ORU_R01 labMsg) {
        //            labMsg.getMSH().getMsh10_MessageControlID();
        //        }

        Bundle bundle = new Bundle();
        // TODO

        return bundle;
    }
}
