package io.github.samarthshubham.clinic.dto;

import java.time.LocalDate;
import java.util.List;

public record CarePlanResponse(
    String id,
    String patientId,
    List<String> conditionIds,
    String title,
    String status,
    LocalDate periodStart,
    List<String> goalIds) {}
