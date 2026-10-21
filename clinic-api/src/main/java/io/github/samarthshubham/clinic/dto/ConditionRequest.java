package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ConditionRequest(
    @NotBlank String patientId,
    String encounterId,
    @NotBlank String code,
    @NotBlank String display,
    @NotBlank String clinicalStatus,
    @NotNull LocalDate onsetDate) {}
