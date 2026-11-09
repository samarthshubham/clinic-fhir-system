package io.github.samarthshubham.payer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.payer.config.SecurityConfig;
import io.github.samarthshubham.payer.dto.OAuthTokenResponse;
import io.github.samarthshubham.payer.exception.OAuthException;
import io.github.samarthshubham.payer.service.ClientCredentialsService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(OAuthTokenController.class)
@Import(SecurityConfig.class)
class OAuthTokenControllerTest {

  private static final OAuthTokenResponse TOKEN =
      new OAuthTokenResponse("system.jwt", "bearer", 300, "system/Claim.c", null);

  @Autowired private MockMvcTester mvc;

  @MockitoBean private ClientCredentialsService clientCredentialsService;

  @Test
  void basicAuthenticatedClientGetsASmartTokenResponse() {
    when(clientCredentialsService.issueToken(
            "client_credentials", "clinic-api", "s3cret:x", "system/Claim.c"))
        .thenReturn(TOKEN);

    assertThat(
            mvc.post()
                .uri("/oauth/token")
                .header(HttpHeaders.AUTHORIZATION, basic("clinic-api", "s3cret%3Ax"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials&scope=system%2FClaim.c"))
        .hasStatusOk()
        .hasHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        .hasHeader(HttpHeaders.PRAGMA, "no-cache")
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"access_token":"system.jwt","token_type":"bearer","expires_in":300,
             "scope":"system/Claim.c"}
            """);
  }

  @Test
  void clientCanAuthenticateWithFormParameters() {
    when(clientCredentialsService.issueToken(
            eq("client_credentials"), eq("clinic-api"), eq("s3cret"), isNull()))
        .thenReturn(TOKEN);

    assertThat(
            mvc.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials&client_id=clinic-api&client_secret=s3cret"))
        .hasStatusOk();
  }

  @Test
  void oauthErrorsUseTheOAuthShape() {
    when(clientCredentialsService.issueToken(any(), any(), any(), any()))
        .thenThrow(OAuthException.invalidClient());

    assertThat(
            mvc.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials&client_id=clinic-api&client_secret=no"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"payer-api\"")
        .hasHeader(HttpHeaders.CACHE_CONTROL, "no-store")
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"error":"invalid_client","error_description":"Client authentication failed"}
            """);
  }

  @Test
  void twoAuthenticationMethodsAreRejected() {
    assertThat(
            mvc.post()
                .uri("/oauth/token")
                .header(HttpHeaders.AUTHORIZATION, basic("clinic-api", "s3cret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials&client_secret=s3cret"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.error")
        .isEqualTo("invalid_request");
    verifyNoInteractions(clientCredentialsService);
  }

  @Test
  void repeatedParameterIsRejected() {
    assertThat(
            mvc.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials&grant_type=client_credentials"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.error_description")
        .isEqualTo("grant_type must not be repeated");
  }

  @Test
  void malformedBasicCredentialsAreInvalidClient() {
    String noColon =
        "Basic "
            + Base64.getEncoder().encodeToString("clinic-api".getBytes(StandardCharsets.UTF_8));

    assertThat(
            mvc.post()
                .uri("/oauth/token")
                .header(HttpHeaders.AUTHORIZATION, noColon)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(
            mvc.post()
                .uri("/oauth/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic %%%")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .content("grant_type=client_credentials"))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private static String basic(String clientId, String secret) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
  }
}
