package io.github.samarthshubham.clinic.dto;

import java.time.LocalDate;

public record GoalResponse(
    String id,
    String patientId,
    String description,
    String status,
    String targetMeasureCode,
    String targetMeasureDisplay,
    Double targetValue,
    String targetUnit,
    LocalDate targetDate) {}
