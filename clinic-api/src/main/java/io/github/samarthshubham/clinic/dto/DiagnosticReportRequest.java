package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record DiagnosticReportRequest(
    @NotBlank String patientId,
    String encounterId,
    String serviceRequestId,
    @NotBlank String title,
    @NotBlank String status,
    @NotNull LocalDate issued,
    String conclusion,
    List<String> resultIds) {}
