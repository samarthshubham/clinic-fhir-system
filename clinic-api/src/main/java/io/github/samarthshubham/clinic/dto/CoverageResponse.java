package io.github.samarthshubham.clinic.dto;

import java.time.LocalDate;

public record CoverageResponse(
    String id,
    String patientId,
    String payerOrganizationId,
    String payerId,
    String payerName,
    String memberId,
    String planName,
    String status,
    LocalDate periodStart,
    LocalDate periodEnd,
    String relationship) {}
