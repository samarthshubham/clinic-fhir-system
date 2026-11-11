package io.github.samarthshubham.payer.service;

public record MemberCoverageCheck(Status status, String payerCoverageId) {

  public enum Status {
    ACTIVE,
    NO_ACTIVE_COVERAGE,
    NO_MEMBER_FOUND
  }

  public boolean verified() {
    return status == Status.ACTIVE;
  }
}
