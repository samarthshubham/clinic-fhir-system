package io.github.samarthshubham.clinic.dto;

import java.util.List;

public record DocumentationResponse(
    String serviceRequestId,
    String questionnaireResponseId,
    String questionnaire,
    String title,
    String status,
    List<DocumentationItem> items) {}
