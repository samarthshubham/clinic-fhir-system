package io.github.samarthshubham.payer.dto;

import java.time.LocalDate;

public record MemberCoverage(
    String id,
    String status,
    LocalDate periodStart,
    LocalDate periodEnd,
    String payerName,
    String relationship,
    PlanSummary plan) {}
