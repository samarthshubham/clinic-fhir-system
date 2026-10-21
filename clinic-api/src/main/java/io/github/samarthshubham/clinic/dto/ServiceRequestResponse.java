package io.github.samarthshubham.clinic.dto;

import java.time.LocalDate;

public record ServiceRequestResponse(
    String id,
    String patientId,
    String encounterId,
    String practitionerId,
    String code,
    String category,
    String status,
    String priority,
    LocalDate authoredOn) {}
