package io.github.samarthshubham.clinic.dto;

import java.util.List;

public record DocumentationItem(
    String linkId,
    String text,
    String type,
    boolean required,
    boolean repeats,
    boolean readOnly,
    List<DocumentationValue> options,
    List<DocumentationEnableWhen> enableWhen,
    String enableBehavior,
    List<DocumentationAnswer> answers,
    List<DocumentationItem> items) {}
