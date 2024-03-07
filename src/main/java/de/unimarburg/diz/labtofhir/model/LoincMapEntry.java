package de.unimarburg.diz.labtofhir.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class LoincMapEntry {

    private String swl;
    private String loinc;
    private String ucum;
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
        this.meta = StringUtils.isBlank(meta) ? null : meta;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LoincMapEntry that = (LoincMapEntry) o;
        return Objects.equals(swl, that.swl) && Objects.equals(loinc,
            that.loinc) && Objects.equals(ucum, that.ucum) && Objects.equals(
            meta, that.meta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(swl, loinc, ucum, meta);
    }
}
