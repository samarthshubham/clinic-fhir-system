package io.github.samarthshubham.clinic.dto;

import java.util.List;

public record DirectoryResponse(String planName, List<DirectoryProvider> providers) {}
