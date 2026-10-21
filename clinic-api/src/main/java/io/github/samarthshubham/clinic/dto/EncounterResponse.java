package io.github.samarthshubham.clinic.dto;

import java.time.LocalDate;

public record EncounterResponse(
    String id,
    String patientId,
    LocalDate visitDate,
    String reason,
    String status,
    String practitionerId,
    String note) {}
