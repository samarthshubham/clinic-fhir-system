package io.github.samarthshubham.clinic.exception;

public class UnauthorizedWebhookException extends RuntimeException {

  public UnauthorizedWebhookException() {
    super("Missing or invalid webhook secret");
  }
}
