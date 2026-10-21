package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CarePlanRequest(
    @NotBlank String patientId,
    List<String> conditionIds,
    @NotBlank String title,
    @NotBlank String status,
    @NotNull LocalDate periodStart,
    List<String> goalIds) {}
