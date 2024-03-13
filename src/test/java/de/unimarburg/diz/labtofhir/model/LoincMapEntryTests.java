package de.unimarburg.diz.labtofhir.model;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;

public class LoincMapEntryTests {

    @Test
    void entryIsEqual() {
        var entry = new LoincMapEntry();
        entry.setSwl("swisslab");
        entry.setLoinc("loinc");
        entry.setUcum("ucum");
        entry.setMeta("meta");
        var entry2 = new LoincMapEntry();
        entry2.setSwl("swisslab");
        entry2.setLoinc("loinc");
        entry2.setUcum("ucum");
        entry2.setMeta("meta");
        var entry3 = new LoincMapEntry();
        entry3.setSwl("swisslab");
        entry3.setLoinc("loinc");
        entry3.setUcum("ucum");
        entry3.setMeta("META");

        assertThat(entry.equals(entry)).isTrue();
        assertThat(entry.equals(entry2)).isTrue();
        assertThat(entry.equals(null)).isFalse();
        assertThat(entry.equals(entry3)).isFalse();
    }

}
