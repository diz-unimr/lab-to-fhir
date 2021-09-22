package de.unimarburg.diz.labtofhir.model;

public enum MetaCode {
    MTYP_POCT {
        public String toString() {
            return "MTYP-POCT";
        }
    },
    DIFFART,
    GRBB
}
