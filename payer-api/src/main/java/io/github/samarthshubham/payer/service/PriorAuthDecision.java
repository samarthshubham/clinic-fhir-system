package io.github.samarthshubham.payer.service;

public record PriorAuthDecision(Outcome outcome, ApprovalCriterion.DenialReason denialReason) {

  public enum Outcome {
    APPROVED,
    DENIED,
    PENDED,
    NOT_REQUIRED
  }

  public PriorAuthDecision {
    if (outcome == Outcome.DENIED && denialReason == null) {
      throw new IllegalArgumentException("A denial needs a reason code and text");
    }
  }

  static PriorAuthDecision of(Outcome outcome) {
    return new PriorAuthDecision(outcome, null);
  }

  static PriorAuthDecision denied(ApprovalCriterion.DenialReason reason) {
    return new PriorAuthDecision(Outcome.DENIED, reason);
  }
}
