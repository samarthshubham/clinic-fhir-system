package io.github.samarthshubham.clinic.dto;

public record ClinicianAlert(String patientReference, String observationReference, String reason) {}
