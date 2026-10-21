package io.github.samarthshubham.clinic.dto;

import java.util.List;
import java.util.Map;

public record SimpleSeries(
    List<DataPoint> values, String unit, Map<String, Object> referenceRange) {}
