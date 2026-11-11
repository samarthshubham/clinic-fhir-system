package io.github.samarthshubham.payer.cdshooks;

import io.github.samarthshubham.payer.service.MemberCoverageCheck;
import java.time.LocalDate;

record CoverageDecision(
    MemberCoverageCheck.Status memberStatus,
    String serviceName,
    boolean priorAuthNeeded,
    boolean priorAuthWaived,
    boolean documentationNeeded,
    String questionnaire) {

  static final int MAX_SUMMARY_LENGTH = 140;

  boolean verified() {
    return memberStatus == MemberCoverageCheck.Status.ACTIVE;
  }

  String summary() {
    String summary =
        !verified()
            ? "Coverage could not be verified"
            : (priorAuthNeeded
                    ? "Prior authorization needed: "
                    : "Covered, no prior authorization needed: ")
                + serviceName;
    return summary.length() <= MAX_SUMMARY_LENGTH
        ? summary
        : summary.substring(0, MAX_SUMMARY_LENGTH - 1) + "…";
  }

  String indicator() {
    return !verified() || priorAuthNeeded ? "warning" : "info";
  }

  String detail(String payerName, LocalDate today) {
    if (memberStatus == MemberCoverageCheck.Status.NO_MEMBER_FOUND) {
      return payerName
          + " has no member with the member id on this coverage. Check the patient's insurance"
          + " details before ordering.";
    }
    if (memberStatus == MemberCoverageCheck.Status.NO_ACTIVE_COVERAGE) {
      return payerName
          + " has no active coverage for this member on "
          + today
          + ". Check the patient's insurance details before ordering.";
    }
    if (documentationNeeded) {
      return payerName
          + " needs prior authorization for this order. Complete the documentation"
          + " questionnaire, then submit the prior authorization request.";
    }
    if (priorAuthNeeded) {
      return payerName + " needs prior authorization for this order.";
    }
    if (priorAuthWaived) {
      return payerName
          + " covers this order. Prior authorization is waived for stat emergency orders.";
    }
    return payerName + " covers this order without prior authorization.";
  }
}
