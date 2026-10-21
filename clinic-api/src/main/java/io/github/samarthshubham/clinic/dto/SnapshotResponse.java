package io.github.samarthshubham.clinic.dto;

import java.util.List;
import java.util.Map;

public record SnapshotResponse(
    List<Map<String, Object>> activeConditions,
    List<Map<String, Object>> currentMedications,
    LatestVitals latestVitals,
    List<AlertItem> alerts) {}
