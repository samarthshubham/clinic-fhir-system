package io.github.samarthshubham.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.AuthUser;
import io.github.samarthshubham.clinic.dto.LoginRequest;
import io.github.samarthshubham.clinic.dto.TokenResponse;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.service.AuthService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  private static final TokenResponse TOKENS =
      new TokenResponse(
          "access.jwt",
          "refresh.jwt",
          "Bearer",
          900,
          new AuthUser(
              "clinician",
              "Anika Patel",
              "Internal Medicine",
              "user-clinician",
              null,
              List.of(Role.CLINICIAN)));

  private static final String TOKENS_JSON =
      """
      {"accessToken":"access.jwt","refreshToken":"refresh.jwt","tokenType":"Bearer",
       "expiresIn":900,
       "user":{"username":"clinician","name":"Anika Patel","title":"Internal Medicine",
               "practitionerId":"user-clinician","patientId":null,"roles":["CLINICIAN"]}}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private AuthService authService;

  @Test
  void loginIsPublicAndReturnsTokens() {
    when(authService.login(new LoginRequest("clinician", "pw"))).thenReturn(TOKENS);

    assertThat(
            mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"clinician\",\"password\":\"pw\"}"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(TOKENS_JSON);
  }

  @Test
  void badCredentialsReturnJson401() {
    when(authService.login(any()))
        .thenThrow(new BadCredentialsException("Invalid username or password"));

    assertThat(
            mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"clinician\",\"password\":\"nope\"}"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":401,"message":"Invalid username or password"}
            """);
  }

  @Test
  void loginWithoutPasswordIsRejected() {
    assertThat(
            mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"clinician\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void refreshExchangesRefreshTokenForNewTokens() {
    when(authService.refresh("refresh.jwt")).thenReturn(TOKENS);

    assertThat(
            mvc.post()
                .uri("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"refresh.jwt\"}"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(TOKENS_JSON);
  }
}
