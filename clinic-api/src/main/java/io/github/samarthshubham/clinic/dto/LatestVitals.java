package io.github.samarthshubham.clinic.dto;

public record LatestVitals(
    VitalReading systolicBp,
    VitalReading diastolicBp,
    VitalReading weightKg,
    VitalReading spo2Percent,
    VitalReading heartRate,
    VitalReading temperature) {}
