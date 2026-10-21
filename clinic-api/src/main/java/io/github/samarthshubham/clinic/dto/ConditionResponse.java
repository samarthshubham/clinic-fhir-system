package io.github.samarthshubham.clinic.dto;

import java.time.LocalDate;

public record ConditionResponse(
    String id,
    String patientId,
    String encounterId,
    String code,
    String display,
    String clinicalStatus,
    LocalDate onsetDate) {}
