package de.unimarburg.diz.labtofhir.model;

public class MetadataResourceField {

    private String name;
    private String type;
    private String format;

    public String getName() {
        return name;
    }

    public MetadataResourceField setName(String name) {
        this.name = name;
        return this;
    }

    public String getType() {
        return type;
    }

    public MetadataResourceField setType(String type) {
        this.type = type;
        return this;
    }

    public String getFormat() {
        return format;
    }

    public MetadataResourceField setFormat(String format) {
        this.format = format;
        return this;
    }
}
