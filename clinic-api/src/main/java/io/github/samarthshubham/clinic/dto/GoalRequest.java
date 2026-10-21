package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record GoalRequest(
    @NotBlank String patientId,
    @NotBlank String description,
    @NotBlank String status,
    String targetMeasureCode,
    String targetMeasureDisplay,
    Double targetValue,
    String targetUnit,
    LocalDate targetDate) {}
