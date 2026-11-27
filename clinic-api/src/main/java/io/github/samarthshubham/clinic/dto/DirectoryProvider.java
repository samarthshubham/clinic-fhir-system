package io.github.samarthshubham.clinic.dto;

import java.util.List;

public record DirectoryProvider(
    String id,
    String name,
    List<String> specialties,
    String location,
    String address,
    String organization) {}
