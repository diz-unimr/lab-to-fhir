package de.unimarburg.diz.labtofhir.mapper;

import java.time.LocalDate;

public record DateFilter(LocalDate date, String comparator) {
}
