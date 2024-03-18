package de.unimarburg.diz.labtofhir.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

public class LoincMapTests {

    @Test
    void diffCreatesCodeDiff() {
        var original = new LoincMap();
        original.put("FOO", new LoincMapEntry.Builder()
            .withLoinc("1000-0")
            .withUcum("mmol/L")
            .build());
        original.put("BAR", new LoincMapEntry.Builder()
            .withLoinc("1000-0")
            .withUcum("mmol/L")
            .build());

        var from = new LoincMap();
        from.put("FOO", new LoincMapEntry.Builder()
            .withLoinc("1000-0")
            .withUcum("mmol/L")
            .build());
        from.put("BAR", new LoincMapEntry.Builder()
            .withLoinc("1111-0")
            .withUcum("mmol/L")
            .build());

        var diff = original.diff(from);

        assertThat(diff).isEqualTo(Set.of("BAR"));
    }

}
