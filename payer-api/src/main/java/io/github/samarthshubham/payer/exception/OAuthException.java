package io.github.samarthshubham.payer.exception;

import org.springframework.http.HttpStatus;

public class OAuthException extends RuntimeException {

  private final HttpStatus status;
  private final String error;

  private OAuthException(HttpStatus status, String error, String description) {
    super(description);
    this.status = status;
    this.error = error;
  }

  public static OAuthException invalidRequest(String description) {
    return new OAuthException(HttpStatus.BAD_REQUEST, "invalid_request", description);
  }

  public static OAuthException invalidClient() {
    return new OAuthException(
        HttpStatus.UNAUTHORIZED, "invalid_client", "Client authentication failed");
  }

  public static OAuthException unsupportedGrantType() {
    return new OAuthException(
        HttpStatus.BAD_REQUEST,
        "unsupported_grant_type",
        "Only grant_type=client_credentials is supported");
  }

  public static OAuthException invalidScope(String description) {
    return new OAuthException(HttpStatus.BAD_REQUEST, "invalid_scope", description);
  }

  public HttpStatus status() {
    return status;
  }

  public String error() {
    return error;
  }
}
