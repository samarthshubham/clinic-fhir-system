package io.github.samarthshubham.clinic.dto;

import java.time.OffsetDateTime;

public record AlertResponse(
    String id,
    String patientId,
    String observationId,
    String reason,
    OffsetDateTime raisedAt,
    String status) {}
