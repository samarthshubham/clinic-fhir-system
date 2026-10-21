package io.github.samarthshubham.clinic.dto;

public record VitalReading(Double value, String unit, String date, Boolean flagged) {}
