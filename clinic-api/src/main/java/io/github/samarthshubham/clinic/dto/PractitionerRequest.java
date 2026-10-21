package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;

public record PractitionerRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotBlank String specialty,
    String npi,
    String email) {}
