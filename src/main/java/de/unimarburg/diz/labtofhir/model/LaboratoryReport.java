package de.unimarburg.diz.labtofhir.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import de.unimarburg.diz.labtofhir.serializer.DiagnosticReportDeserializer;
import de.unimarburg.diz.labtofhir.serializer.FhirSerializer;
import de.unimarburg.diz.labtofhir.serializer.InstantDeserializer;
import de.unimarburg.diz.labtofhir.serializer.ObservationListDeserializer;
import de.unimarburg.diz.labtofhir.serializer.ObservationListSerializer;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;

public class LaboratoryReport implements Serializable {

    private int id;
    private Instant inserted;
    private Instant modified;
    private Instant deleted;
    private DiagnosticReport resource;
    private List<Observation> observations;
    private String metaCode;

    //    @JsonSerialize(contentUsing = FhirSerializer.class)
    @JsonSerialize(using = ObservationListSerializer.class)
    public List<Observation> getObservations() {
        return observations;
    }

    @JsonSetter("fhir_obs")
    @JsonDeserialize(using = ObservationListDeserializer.class)
    //    @JsonDeserialize(using = ObservationListDeserializer.class)
    public void setObservations(List<Observation> observations) {
        this.observations = observations;
    }

    public int getId() {
        return id;
    }

    @JsonSetter("id")
    public void setId(int id) {
        this.id = id;
    }

    public Instant getInserted() {
        return inserted;
    }

    @JsonSetter("inserted_when")
    @JsonDeserialize(using = InstantDeserializer.class)
    public void setInserted(Instant inserted) {
        this.inserted = inserted;
    }

    public Instant getModified() {
        return modified;
    }

    @JsonSetter("modified")
    @JsonDeserialize(using = InstantDeserializer.class)
    public void modified(Instant modified) {
        this.modified = modified;
    }

    public Instant getDeleted() {
        return deleted;
    }

    @JsonSetter("deleted_when")
    @JsonDeserialize(using = InstantDeserializer.class)
    public void setDeleted(Instant deleted) {
        this.deleted = deleted;
    }

    @JsonSerialize(using = FhirSerializer.class)
    public DiagnosticReport getResource() {
        return resource;
    }

    @JsonSetter("fhir")
    @JsonDeserialize(using = DiagnosticReportDeserializer.class)
    public void setResource(DiagnosticReport resource) {
        this.resource = resource;
        sanitizeIdentifierValue(this.resource);
    }

    private void sanitizeIdentifierValue(DiagnosticReport resource) {
        var identifierValue = resource
            .getIdentifierFirstRep()
            .getValue();
        var idPart = StringUtils.substringAfterLast(identifierValue, "_");
        if (StringUtils.isNotBlank(idPart)) {
            resource
                .getIdentifierFirstRep()
                .setValue(idPart);
        }
    }

    public String getReportIdentifierValue() {
        return resource
            .getIdentifierFirstRep()
            .getValue();
    }

    public String getMetaCode() {
        return this.metaCode;
    }

    public void setMetaCode(String code) {
        this.metaCode = code;
    }
}
