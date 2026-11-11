package io.github.samarthshubham.payer.cdshooks;

import org.springframework.http.HttpStatus;

public class CdsHookException extends RuntimeException {

  private final HttpStatus status;

  public CdsHookException(HttpStatus status, String message) {
    super(message);
    this.status = status;
  }

  public CdsHookException(HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
