package io.github.shubhamforge.clinic.dto;

public record ClinicianAlert(String patientReference, String observationReference, String reason) {}
