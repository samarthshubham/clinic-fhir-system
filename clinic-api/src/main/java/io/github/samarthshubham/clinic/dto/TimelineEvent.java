package io.github.samarthshubham.clinic.dto;

import java.util.List;
import java.util.Map;

public record TimelineEvent(
    String id,
    String type,
    String date,
    String title,
    String subtitle,
    String status,
    String resourceId,
    Map<String, Object> metadata,
    String chiefComplaint,
    String note,
    List<GroupedObservation> groupedObservations,
    String linkedTo,
    String linkedFrom) {}
