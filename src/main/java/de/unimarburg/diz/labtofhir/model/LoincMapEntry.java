package de.unimarburg.diz.labtofhir.model;

public class LoincMapEntry {

    private String loinc;
    private String ucum;
    private String meta;

    private String version;

    public String getVersion() {
        return version;
    }

    public LoincMapEntry setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getMeta() {
        return meta;
    }

    public LoincMapEntry setMeta(String meta) {
        this.meta = meta;
        return this;
    }

    public String getLoinc() {
        return this.loinc;
    }

    public LoincMapEntry setLoinc(String loinc) {
        this.loinc = loinc;
        return this;
    }

    public String getUcum() {
        return this.ucum;
    }

    public LoincMapEntry setUcum(String ucum) {
        this.ucum = ucum;
        return this;
    }

}
