package de.unimarburg.diz.labtofhir.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;

public class TagCounter {

    private final String name;
    private final String description;
    private final String tag;
    private final MeterRegistry registry;
    private final Map<String, Counter> counters = new HashMap<>();

    public TagCounter(String name, String description, String tag,
        MeterRegistry registry) {
        this.name = name;
        this.description = description;
        this.tag = tag;
        this.registry = registry;
    }

    public void increment(String tagValue) {
        var counter = counters.computeIfAbsent(tagValue, t -> Counter
            .builder(name)
            .description(description)
            .tags(tag, t)
            .register(registry));

        counter.increment();
    }
}
