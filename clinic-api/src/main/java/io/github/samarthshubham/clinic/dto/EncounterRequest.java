package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record EncounterRequest(
    @NotBlank String patientId,
    @NotNull LocalDate visitDate,
    String reason,
    @NotBlank String status,
    String practitionerId,
    String note) {}
