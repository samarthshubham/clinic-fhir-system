package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ServiceRequestRequest(
    @NotBlank String patientId,
    String encounterId,
    String practitionerId,
    @NotBlank String code,
    @NotBlank String category,
    @NotBlank String status,
    @NotBlank String priority,
    @NotNull LocalDate authoredOn) {}
