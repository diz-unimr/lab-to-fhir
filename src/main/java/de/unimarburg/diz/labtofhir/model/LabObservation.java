package de.unimarburg.diz.labtofhir.model;

import java.io.Serializable;
import org.hl7.fhir.r4.model.Observation;

public class LabObservation implements Serializable {

    private Observation value;
    private String metaCode;

    public Observation getValue() {
        return value;
    }

    public LabObservation setValue(Observation value) {
        this.value = value;
        return this;
    }

    public String getMetaCode() {
        return metaCode;
    }

    public LabObservation setMetaCode(String metaCode) {
        this.metaCode = metaCode;
        return this;
    }
}
