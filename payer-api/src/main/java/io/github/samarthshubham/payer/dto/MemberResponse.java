package io.github.samarthshubham.payer.dto;

import java.time.LocalDate;
import java.util.List;

public record MemberResponse(
    String id,
    String memberId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String gender,
    List<MemberCoverage> coverages,
    List<PriorAuthSummary> priorAuths) {}
