package de.unimarburg.diz.labtofhir.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Objects;

@JsonDeserialize(builder = LoincMapEntry.Builder.class)
public final class LoincMapEntry {

    private final String swl;
    private final String loinc;
    private final String ucum;
    private final String meta;

    private LoincMapEntry(String swl, String loinc, String ucum, String meta) {
        this.swl = swl;
        this.loinc = loinc;
        this.ucum = ucum;
        this.meta = meta;
    }

    public String getSwl() {
        return this.swl;
    }

    public String getMeta() {
        return meta;
    }

    public String getLoinc() {
        return this.loinc;
    }

    public String getUcum() {
        return this.ucum;
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

    @JsonPOJOBuilder
    public static class Builder {

        @JsonProperty("CODE")
        private String code;
        @JsonProperty("LOINC")
        private String loinc;
        @JsonProperty("UCUM_WERT")
        private String ucum;
        @JsonProperty("QUELLE")
        private String meta;

        public Builder withCode(String swl) {
            this.code = swl;
            return this;
        }

        public Builder withLoinc(String loinc) {
            this.loinc = loinc;
            return this;
        }

        public Builder withUcum(String ucum) {
            this.ucum = ucum;
            return this;
        }

        public Builder withMeta(String meta) {
            this.meta = meta;
            return this;
        }


        public LoincMapEntry build() {
            return new LoincMapEntry(code, loinc, ucum, meta);
        }
    }
}
