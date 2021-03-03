package de.unimarburg.diz.labtofhir.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.time.Instant;
import org.hl7.fhir.r4.model.DiagnosticReport;

public class LaboratoryReport {

    private int id;
    private Instant inserted;
    private Instant modified;
    private Instant deleted;
    private DiagnosticReport resource;

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
    public void setInserted(Instant inserted) {
        this.inserted = inserted;
    }

    public Instant getModified() {
        return modified;
    }

    @JsonSetter("modified")
    public void modified(Instant modified) {
        this.modified = modified;
    }

    public Instant getDeleted() {
        return deleted;
    }

    @JsonSetter("deleted_when")
    public void setDeleted(Instant deleted) {
        this.deleted = deleted;
    }

    public DiagnosticReport getResource() {
        return resource;
    }

    @JsonSetter("fhir")
    public void setResource(DiagnosticReport resource) {
        this.resource = resource;
    }

    public String getReportIdentifierValue() {
        return resource.getIdentifierFirstRep()
            .getValue();
    }
}
