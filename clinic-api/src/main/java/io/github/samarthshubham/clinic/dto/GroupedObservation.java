package io.github.samarthshubham.clinic.dto;

public record GroupedObservation(
    String type, String display, Double value, String unit, boolean flagged) {}
