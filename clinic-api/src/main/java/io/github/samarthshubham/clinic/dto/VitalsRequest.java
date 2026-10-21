package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record VitalsRequest(
    @NotBlank String patientId,
    String encounterId,
    @NotNull LocalDate effectiveDate,
    Integer systolicBp,
    Integer diastolicBp,
    Double weightKg,
    Double spo2Percent,
    Integer heartRateBpm,
    Double temperatureCelsius) {}
