package io.github.samarthshubham.clinic.dto;

import java.time.LocalDate;

public record MedicationResponse(
    String id,
    String patientId,
    String medicationName,
    String status,
    String dosageText,
    LocalDate startDate) {}
