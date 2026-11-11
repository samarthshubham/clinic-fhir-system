package io.github.samarthshubham.payer.service;

import static io.github.samarthshubham.payer.support.TestAuth.CLIENT_ID;
import static io.github.samarthshubham.payer.support.TestAuth.CLIENT_SECRET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.github.samarthshubham.payer.config.AuthProperties;
import io.github.samarthshubham.payer.exception.OAuthException;
import io.github.samarthshubham.payer.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ClientCredentialsServiceTest {

  private final AuthProperties properties = TestAuth.properties();
  private final ClientCredentialsService service =
      new ClientCredentialsService(
          properties, new BCryptPasswordEncoder(), TestAuth.tokenService(properties));

  @Test
  void noScopeRequestedGrantsEveryRegisteredScope() {
    assertThat(service.issueToken("client_credentials", CLIENT_ID, CLIENT_SECRET, null).scope())
        .isEqualTo("system/Claim.c system/Coverage.r system/Questionnaire.r");
  }

  @Test
  void requestedSubsetIsGranted() {
    assertThat(
            service
                .issueToken("client_credentials", CLIENT_ID, CLIENT_SECRET, " system/Claim.c ")
                .scope())
        .isEqualTo("system/Claim.c");
  }

  @Test
  void unregisteredScopeIsRejected() {
    assertOAuthError(
        () ->
            service.issueToken(
                "client_credentials", CLIENT_ID, CLIENT_SECRET, "system/Claim.c system/*.cruds"),
        HttpStatus.BAD_REQUEST,
        "invalid_scope");
  }

  @Test
  void wrongSecretUnknownClientAndMissingSecretAreInvalidClient() {
    assertOAuthError(
        () -> service.issueToken("client_credentials", CLIENT_ID, "wrong", null),
        HttpStatus.UNAUTHORIZED,
        "invalid_client");
    assertOAuthError(
        () -> service.issueToken("client_credentials", "someone-else", CLIENT_SECRET, null),
        HttpStatus.UNAUTHORIZED,
        "invalid_client");
    assertOAuthError(
        () -> service.issueToken("client_credentials", CLIENT_ID, null, null),
        HttpStatus.UNAUTHORIZED,
        "invalid_client");
  }

  @Test
  void grantTypeMustBeClientCredentials() {
    assertOAuthError(
        () -> service.issueToken(null, CLIENT_ID, CLIENT_SECRET, null),
        HttpStatus.BAD_REQUEST,
        "invalid_request");
    assertOAuthError(
        () -> service.issueToken("password", CLIENT_ID, CLIENT_SECRET, null),
        HttpStatus.BAD_REQUEST,
        "unsupported_grant_type");
  }

  @Test
  void unknownClientStillPaysForAPasswordCheck() {
    BCryptPasswordEncoder encoder = spy(new BCryptPasswordEncoder(4));
    ClientCredentialsService timedService =
        new ClientCredentialsService(properties, encoder, TestAuth.tokenService(properties));

    assertOAuthError(
        () -> timedService.issueToken("client_credentials", "someone-else", "guess", null),
        HttpStatus.UNAUTHORIZED,
        "invalid_client");
    verify(encoder).matches(eq("guess"), anyString());
  }

  private static void assertOAuthError(Runnable call, HttpStatus status, String error) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            OAuthException.class,
            e -> {
              assertThat(e.status()).isEqualTo(status);
              assertThat(e.error()).isEqualTo(error);
            });
  }
}
