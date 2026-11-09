package io.github.samarthshubham.payer.dto;

import java.time.LocalDate;

public record PriorAuthSummary(
    String id,
    String status,
    LocalDate submitted,
    String service,
    String outcome,
    String preAuthRef) {}
