package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PatientRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull LocalDate dateOfBirth,
    @NotBlank String gender,
    String phone,
    String email) {}
