package io.github.samarthshubham.payer.dto;

import java.time.LocalDate;

public record MemberSummary(
    String id,
    String memberId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String gender,
    String planName,
    String coverageStatus) {}
