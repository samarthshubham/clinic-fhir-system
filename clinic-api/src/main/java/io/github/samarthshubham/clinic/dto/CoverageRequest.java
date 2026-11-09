package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public record CoverageRequest(
    @NotBlank String patientId,
    @NotBlank
        @Pattern(
            regexp = "[A-Za-z0-9._-]+",
            message = "payerId may only contain letters, digits, '.', '_' and '-'")
        String payerId,
    @NotBlank String payerName,
    @NotBlank String memberId,
    String planName,
    @NotBlank
        @Pattern(
            regexp = "active|cancelled|draft|entered-in-error",
            message = "status must be active, cancelled, draft or entered-in-error")
        String status,
    @NotNull LocalDate periodStart,
    LocalDate periodEnd,
    @Pattern(
            regexp = "child|parent|spouse|common|other|self|injured",
            message = "relationship must be a subscriber-relationship code")
        String relationship) {}
