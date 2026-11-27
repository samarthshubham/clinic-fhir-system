package io.github.samarthshubham.payer.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PriorAuthDecisionRequest(
    @NotNull Decision decision,
    @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}") String reasonCode,
    @Size(max = 1000) String reasonText) {

  public enum Decision {
    APPROVE,
    DENY,
    PEND
  }

  @AssertTrue(message = "A denial requires a reason code and reason text")
  public boolean isDenialReasonPresent() {
    return decision != Decision.DENY
        || (reasonCode != null && reasonText != null && !reasonText.isBlank());
  }
}
