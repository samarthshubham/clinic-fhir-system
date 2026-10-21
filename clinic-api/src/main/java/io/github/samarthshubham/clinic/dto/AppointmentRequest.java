package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record AppointmentRequest(
    @NotBlank String patientId,
    String practitionerId,
    @NotNull LocalDateTime start,
    @NotNull LocalDateTime end,
    String description,
    @NotBlank String status) {}
