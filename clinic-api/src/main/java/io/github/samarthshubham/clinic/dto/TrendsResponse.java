package io.github.samarthshubham.clinic.dto;

import java.util.Map;

public record TrendsResponse(String period, String from, String to, Map<String, Object> series) {}
