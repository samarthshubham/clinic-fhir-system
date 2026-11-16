package io.github.samarthshubham.clinic.dto;

import java.time.LocalDate;
import java.util.List;

public record ServiceRequestResponse(
    String id,
    String patientId,
    String encounterId,
    String practitionerId,
    String display,
    String codeSystem,
    String code,
    String category,
    String status,
    String priority,
    LocalDate authoredOn,
    CoverageInformation coverage,
    List<CrdCard> cards) {}
