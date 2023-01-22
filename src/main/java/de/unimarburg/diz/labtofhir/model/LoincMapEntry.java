package de.unimarburg.diz.labtofhir.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import org.apache.commons.lang3.StringUtils;

public class LoincMapEntry {

    private String swl;
    private String loinc;
    private String ucum;
    private boolean groupCode;
    private String meta;

    public String getSwl() {
        return this.swl;
    }

    @JsonSetter("CODE")
    public void setSwl(String swl) {
        this.swl = swl;
    }

    public String getMeta() {
        return meta;
    }

    @JsonSetter("QUELLE")
    public LoincMapEntry setMeta(String meta) {
        if ("".equals(meta)) {
            meta = null;
        }
        this.meta = meta;
        return this;
    }

    public String getLoinc() {
        return this.loinc;
    }

    @JsonSetter("LOINC")
    public LoincMapEntry setLoinc(String loinc) {
        this.loinc = loinc;
        return this;
    }

    public String getUcum() {
        return this.ucum;
    }

    @JsonSetter("UCUM_WERT")
    public LoincMapEntry setUcum(String ucum) {
        this.ucum = ucum;
        return this;
    }

    public boolean getGroupCode() {
        return groupCode;
    }

    @JsonSetter("ETL_Staging")
    public LoincMapEntry setGroupCode(String groupCode) {
        this.groupCode = StringUtils.equalsIgnoreCase("X", groupCode);
        return this;
    }

}
