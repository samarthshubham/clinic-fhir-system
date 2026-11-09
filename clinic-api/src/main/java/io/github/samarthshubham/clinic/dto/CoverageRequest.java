package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CoverageRequest(
    @NotBlank String patientId,
    @NotBlank String payerId,
    @NotBlank String payerName,
    @NotBlank String memberId,
    String planName,
    @NotBlank String status,
    @NotNull LocalDate periodStart,
    LocalDate periodEnd,
    String relationship) {}
