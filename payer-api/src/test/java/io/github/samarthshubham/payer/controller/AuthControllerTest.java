package io.github.samarthshubham.payer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.payer.config.SecurityConfig;
import io.github.samarthshubham.payer.dto.AuthUser;
import io.github.samarthshubham.payer.dto.LoginRequest;
import io.github.samarthshubham.payer.dto.TokenResponse;
import io.github.samarthshubham.payer.security.Role;
import io.github.samarthshubham.payer.service.AuthService;
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
          new AuthUser("reviewer", "Dana Whitfield", "UM nurse", List.of(Role.UM_REVIEWER)));

  private static final String TOKENS_JSON =
      """
      {"accessToken":"access.jwt","refreshToken":"refresh.jwt","tokenType":"Bearer",
       "expiresIn":900,
       "user":{"username":"reviewer","name":"Dana Whitfield","title":"UM nurse",
               "roles":["UM_REVIEWER"]}}
      """;

  @Autowired private MockMvcTester mvc;

  @MockitoBean private AuthService authService;

  @Test
  void loginIsPublicAndReturnsTokens() {
    when(authService.login(new LoginRequest("reviewer", "pw"))).thenReturn(TOKENS);

    assertThat(
            mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"reviewer\",\"password\":\"pw\"}"))
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
                .content("{\"username\":\"reviewer\",\"password\":\"nope\"}"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":401,"message":"Invalid username or password"}
            """);
  }

  @Test
  void blankFieldsReturn400() {
    assertThat(
            mvc.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":400,"message":"password must not be blank, username must not be blank"}
            """);
  }

  @Test
  void refreshIsPublicAndReturnsTokens() {
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
