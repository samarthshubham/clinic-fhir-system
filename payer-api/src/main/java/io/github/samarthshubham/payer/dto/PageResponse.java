package io.github.samarthshubham.payer.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, int total) {}
