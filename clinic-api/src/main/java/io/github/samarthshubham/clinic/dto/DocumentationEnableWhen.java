package io.github.samarthshubham.clinic.dto;

public record DocumentationEnableWhen(
    String question, String operator, DocumentationValue answer) {}
