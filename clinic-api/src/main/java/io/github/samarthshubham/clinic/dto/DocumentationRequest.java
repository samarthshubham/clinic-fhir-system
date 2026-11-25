package io.github.samarthshubham.clinic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record DocumentationRequest(
    @NotNull List<@Valid @NotNull DocumentationItemAnswers> answers) {}
