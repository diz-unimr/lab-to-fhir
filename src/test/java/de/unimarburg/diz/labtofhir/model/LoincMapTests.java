package de.unimarburg.diz.labtofhir.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

public class LoincMapTests {

    @Test
    void diffCreatesCodeDiff() {
        var original = new LoincMap();
        original.put("FOO", new LoincMapEntry()
            .setLoinc("1000-0")
            .setUcum("mmol/L"));
        original.put("BAR", new LoincMapEntry()
            .setLoinc("1000-0")
            .setUcum("mmol/L"));

        var from = new LoincMap();
        from.put("FOO", new LoincMapEntry()
            .setLoinc("1000-0")
            .setUcum("mmol/L"));
        from.put("BAR", new LoincMapEntry()
            .setLoinc("1111-0")
            .setUcum("mmol/L"));

        var diff = original.diff(from);

        assertThat(diff).isEqualTo(Set.of("BAR"));
    }

}
