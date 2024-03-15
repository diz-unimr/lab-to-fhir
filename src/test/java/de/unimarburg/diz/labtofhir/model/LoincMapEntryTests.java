package de.unimarburg.diz.labtofhir.model;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class LoincMapEntryTests {

    @Test
    public void entryIsEqual() {
        EqualsVerifier
            .forClass(LoincMapEntry.class)
            .verify();
    }

}
