package io.github.samarthshubham.payer.dto;

import java.util.List;

public record DirectoryProvider(
    String id,
    String name,
    List<String> specialties,
    String location,
    String address,
    String organization) {}
