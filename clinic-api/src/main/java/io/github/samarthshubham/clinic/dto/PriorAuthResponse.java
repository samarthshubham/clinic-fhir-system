package io.github.samarthshubham.clinic.dto;

public record PriorAuthResponse(
    String orderId,
    Status status,
    String authorizationNumber,
    String denialReasonCode,
    String denialReason) {

  public enum Status {
    SUBMITTED,
    PENDED,
    APPROVED,
    DENIED,
    NOT_REQUIRED,
    MIXED
  }
}
