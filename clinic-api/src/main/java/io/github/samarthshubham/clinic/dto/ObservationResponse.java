package io.github.samarthshubham.clinic.dto;

public record ObservationResponse(
    String id,
    String patientId,
    String encounterId,
    String type,
    String loincCode,
    Double value,
    String unit,
    String effectiveDateTime) {}
