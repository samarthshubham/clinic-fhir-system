package io.github.samarthshubham.clinic.dto;

import java.time.LocalDate;

public record CoverageInformation(
    String coverageId,
    String covered,
    String paNeeded,
    String docNeeded,
    String reason,
    String questionnaire,
    LocalDate date,
    String coverageAssertionId) {}
