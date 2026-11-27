package io.github.samarthshubham.clinic.dto;

public record ClinicianAlert(
    String patientReference, String sourceReference, String reason, Kind kind) {

  public ClinicianAlert(String patientReference, String sourceReference, String reason) {
    this(patientReference, sourceReference, reason, Kind.HIGH_SYSTOLIC_BP);
  }

  public enum Kind {
    HIGH_SYSTOLIC_BP,
    PRIOR_AUTH_DECISION
  }
}
