package de.unimarburg.diz.labtofhir.stream;

import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class LoincMap {

    private String swl;
    private List<LoincMapEntry> entries;

    public String getSwl() {
        return this.swl;
    }

    //    @JsonSetter("CODE")
    public LoincMap setSwl(String swl) {
        this.swl = swl;
        return this;
    }

    public List<LoincMapEntry> getEntries() {
        return entries;
    }

    public LoincMap setEntries(List<LoincMapEntry> entries) {
        this.entries = entries;
        return this;
    }

    public LoincMapEntry entry() {
        return entries
            .stream()
            .filter(e -> e.getMeta() == null)
            .findAny()
            .orElse(null);
    }

    public LoincMapEntry entry(String meta) {
        return entries
            .stream()
            .filter(e -> StringUtils.equals(e.getMeta(), meta))
            .findAny()
            // fallback to empty meta
            .orElseGet(this::entry);

    }
}
