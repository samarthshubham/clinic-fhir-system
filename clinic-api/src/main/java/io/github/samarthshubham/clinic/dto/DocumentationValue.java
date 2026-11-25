package io.github.samarthshubham.clinic.dto;

import java.math.BigDecimal;

public record DocumentationValue(
    String valueString,
    Integer valueInteger,
    BigDecimal valueDecimal,
    Boolean valueBoolean,
    String valueDate,
    String valueDateTime,
    DocumentationCoding valueCoding) {}
