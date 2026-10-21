package io.github.samarthshubham.clinic.dto;

public record PractitionerResponse(
    String id, String firstName, String lastName, String specialty, String npi, String email) {}
