package io.github.samarthshubham.clinic.dto;

import java.time.LocalDate;
import java.util.List;

public record DiagnosticReportResponse(
    String id,
    String patientId,
    String encounterId,
    String serviceRequestId,
    String title,
    String status,
    LocalDate issued,
    String conclusion,
    List<String> resultIds) {}
