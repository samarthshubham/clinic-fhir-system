package io.github.samarthshubham.clinic.dto;

public record PatientResponse(
    String id,
    String firstName,
    String lastName,
    String dateOfBirth,
    String gender,
    String phone,
    String email,
    Boolean active) {}
