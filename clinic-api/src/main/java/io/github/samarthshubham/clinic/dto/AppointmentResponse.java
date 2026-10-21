package io.github.samarthshubham.clinic.dto;

import java.time.OffsetDateTime;

public record AppointmentResponse(
    String id,
    String patientId,
    String practitionerId,
    OffsetDateTime start,
    OffsetDateTime end,
    String description,
    String status) {}
