package de.unimarburg.diz.labtofhir.metric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

public class TagCounterTests {

    private final MeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void incrementAddsMissingCounter() {
        var name = "total_codes";
        var tag = "code";
        var sut = new TagCounter(name, "This is a dummy counter metric", tag,
            registry);

        sut.increment("new_code");

        var counters = registry
            .find(name)
            .counters()
            .stream()
            .toList();

        var counter = counters
            .stream()
            .filter(c -> c
                .getId()
                .getTags()
                .contains(Tag.of(tag, "new_code")))
            .findAny();

        assertTrue(counter.isPresent());
        assertEquals(counter
            .get()
            .count(), 1.0);
    }

}
