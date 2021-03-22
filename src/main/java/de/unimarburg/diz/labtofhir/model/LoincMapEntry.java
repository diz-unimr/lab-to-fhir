package de.unimarburg.diz.labtofhir.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import org.apache.commons.lang3.StringUtils;

public class LoincMapEntry {

    private String swl;
    private String loinc;
    private String ucum;
    private boolean groupCode;
    private String source;
    private boolean validated;

    public boolean isValidated() {
        return validated;
    }

    @JsonSetter("Validiert")
    public void setValidated(String validated) {
        this.validated = StringUtils.equalsIgnoreCase("X", validated);
    }

    public String getSwl() {
        return this.swl;
    }

    @JsonSetter("CODE")
    public void setSwl(String swl) {
        this.swl = swl;
    }

    public String getSource() {
        return source;
    }

    @JsonSetter("SOURCE")
    public void setSource(String source) {
        if ("".equals(source)) {
            source = null;
        }
        this.source = source;
    }

    public String getLoinc() {
        return this.loinc;
    }

    @JsonSetter("LOINC")
    public void setLoinc(String loinc) {
        this.loinc = loinc;
    }

    public String getUcum() {
        return this.ucum;
    }

    @JsonSetter("UCUM_WERT")
    public void setUcum(String ucum) {
        this.ucum = ucum;
    }

    public boolean getGroupCode() {
        return groupCode;
    }

    @JsonSetter("ETL_Staging")
    public void setGroupCode(String groupCode) {
        this.groupCode = StringUtils.equalsIgnoreCase("X", groupCode);
    }
}
