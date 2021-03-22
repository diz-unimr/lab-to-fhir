package de.unimarburg.diz.labtofhir.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.unimarburg.diz.labtofhir.serializer.DiagnosticReportDeserializer;
import de.unimarburg.diz.labtofhir.serializer.InstantDeserializer;
import java.io.Serializable;
import java.time.Instant;
import org.hl7.fhir.r4.model.DiagnosticReport;

public class LaboratoryReport implements Serializable {

    private int id;
    private Instant inserted;
    private Instant modified;
    private Instant deleted;
    private DiagnosticReport resource;
    private String metaCode;

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

    public DiagnosticReport getResource() {
        return resource;
    }

    @JsonSetter("fhir")
    @JsonDeserialize(using = DiagnosticReportDeserializer.class)
    public void setResource(DiagnosticReport resource) {
        this.resource = resource;
    }

    public String getReportIdentifierValue() {
        return resource.getIdentifierFirstRep()
            .getValue();
    }

    public String getMetaCode() {
        return this.metaCode;
    }

    public void setMetaCode(String code) {
        this.metaCode = code;
    }
}
