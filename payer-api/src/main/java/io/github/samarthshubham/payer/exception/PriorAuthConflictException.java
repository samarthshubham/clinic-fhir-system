package io.github.samarthshubham.payer.exception;

public class PriorAuthConflictException extends RuntimeException {

  public PriorAuthConflictException() {
    super(
        "The prior authorization is no longer available for this decision. Reload and try again.");
  }
}
