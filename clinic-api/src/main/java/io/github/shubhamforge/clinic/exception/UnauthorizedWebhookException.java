package io.github.shubhamforge.clinic.exception;

public class UnauthorizedWebhookException extends RuntimeException {

  public UnauthorizedWebhookException() {
    super("Missing or invalid webhook secret");
  }
}
