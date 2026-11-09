package io.github.samarthshubham.clinic.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ServiceRequestRequest(
    @NotBlank String patientId,
    String encounterId,
    String practitionerId,
    @NotBlank String display,
    String codeSystem,
    String code,
    @NotBlank String category,
    @NotBlank String status,
    @NotBlank String priority,
    @NotNull LocalDate authoredOn) {

  @AssertTrue(message = "codeSystem and code must be given together")
  public boolean isCodingComplete() {
    return (codeSystem == null) == (code == null);
  }
}
