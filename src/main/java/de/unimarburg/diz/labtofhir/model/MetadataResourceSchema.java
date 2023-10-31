package de.unimarburg.diz.labtofhir.model;

import java.util.List;

public class MetadataResourceSchema {

    private List<MetadataResourceField> fields;
    private List<String> missingValues;

    public List<MetadataResourceField> getFields() {
        return fields;
    }

    public MetadataResourceSchema setFields(
        List<MetadataResourceField> fields) {
        this.fields = fields;
        return this;
    }

    public List<String> getMissingValues() {
        return missingValues;
    }

    //    @JsonIgnore
    public MetadataResourceSchema setMissingValues(List<String> missingValues) {
        this.missingValues = missingValues;
        return this;
    }
}
