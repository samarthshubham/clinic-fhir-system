package io.github.samarthshubham.clinic.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, int total) {}
