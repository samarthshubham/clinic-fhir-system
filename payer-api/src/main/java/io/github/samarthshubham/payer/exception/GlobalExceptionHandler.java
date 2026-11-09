package io.github.samarthshubham.payer.exception;

import java.util.stream.Collectors;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorResponse(401, ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest().body(new ErrorResponse(400, message));
  }

  @ExceptionHandler(OAuthException.class)
  public ResponseEntity<OAuthErrorResponse> handleOAuth(OAuthException ex) {
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(ex.status())
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.PRAGMA, "no-cache");
    if (ex.status() == HttpStatus.UNAUTHORIZED) {
      response.header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"payer-api\"");
    }
    return response.body(new OAuthErrorResponse(ex.error(), ex.getMessage()));
  }
}
