package io.github.samarthshubham.clinic.dto;

import java.util.List;
import java.util.Map;

public record BpSeries(
    List<DataPoint> systolic, List<DataPoint> diastolic, Map<String, Integer> referenceRange) {}
