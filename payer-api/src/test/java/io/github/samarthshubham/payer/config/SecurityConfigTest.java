package io.github.samarthshubham.payer.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.payer.controller.AuthController;
import io.github.samarthshubham.payer.security.Role;
import io.github.samarthshubham.payer.service.AuthService;
import io.github.samarthshubham.payer.service.TokenService;
import io.github.samarthshubham.payer.support.TestAuth;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

  @Autowired private MockMvcTester mvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private SecretKey jwtSigningKey;
  @Autowired private AuthProperties authProperties;

  @MockitoBean private AuthService authService;

  @Test
  void protectedPathWithoutCredentialsReturnsJson401() {
    assertThat(mvc.get().uri("/api/members"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasContentType(MediaType.APPLICATION_JSON)
        .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":401,"message":"Authentication required"}
            """);
  }

  @Test
  void validStaffTokenAuthenticatesTheRequest() {
    assertThat(mvc.get().uri("/api/members").header("Authorization", "Bearer " + accessToken()))
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void refreshTokenUsedAsBearerIsRejected() {
    String refreshToken =
        tokenService().issueStaffTokens(TestAuth.user(Role.UM_REVIEWER)).refreshToken();

    assertThat(mvc.get().uri("/api/members").header("Authorization", "Bearer " + refreshToken))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":401,"message":"Invalid or expired token"}
            """);
  }

  private String accessToken() {
    return tokenService().issueStaffTokens(TestAuth.user(Role.PAYER_ADMIN)).accessToken();
  }

  private TokenService tokenService() {
    return new TokenService(jwtEncoder, jwtSigningKey, authProperties);
  }
}
