package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record MedicationRequest(
    @NotBlank String patientId,
    @NotBlank String medicationName,
    @NotBlank String status,
    String dosageText,
    @NotNull LocalDate startDate) {}
