package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DocumentationItemAnswers(
    @NotBlank String linkId, @NotNull List<@NotNull DocumentationValue> values) {}
