package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.payer.config.AuthProperties;
import io.github.samarthshubham.payer.config.SecurityConfig;
import io.github.samarthshubham.payer.dto.AuthUser;
import io.github.samarthshubham.payer.dto.LoginRequest;
import io.github.samarthshubham.payer.dto.TokenResponse;
import io.github.samarthshubham.payer.security.Role;
import io.github.samarthshubham.payer.support.TestAuth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthServiceTest {

  private final AuthProperties properties = TestAuth.properties();
  private final AuthService authService = authService(properties);

  @Test
  void loginReturnsTokensForTheStaffMember() {
    TokenResponse tokens = authService.login(new LoginRequest("reviewer", TestAuth.PASSWORD));

    assertThat(tokens.user())
        .isEqualTo(
            new AuthUser("reviewer", "Dana Whitfield", "UM nurse", List.of(Role.UM_REVIEWER)));
    assertThat(tokens.tokenType()).isEqualTo("Bearer");
  }

  @Test
  void wrongPasswordAndUnknownUserGetTheSameMessage() {
    assertThatThrownBy(() -> authService.login(new LoginRequest("reviewer", "wrong")))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid username or password");
    assertThatThrownBy(() -> authService.login(new LoginRequest("nobody", TestAuth.PASSWORD)))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid username or password");
  }

  @Test
  void refreshIssuesNewTokens() {
    String refreshToken =
        authService.login(new LoginRequest("payeradmin", TestAuth.PASSWORD)).refreshToken();

    assertThat(authService.refresh(refreshToken).user().roles()).containsExactly(Role.PAYER_ADMIN);
  }

  @Test
  void refreshFailsOnceTheAccountIsRemoved() {
    String refreshToken =
        authService.login(new LoginRequest("reviewer", TestAuth.PASSWORD)).refreshToken();
    AuthProperties withoutUsers =
        new AuthProperties(
            properties.jwtSecret(),
            properties.issuer(),
            properties.accessTokenTtl(),
            properties.refreshTokenTtl(),
            properties.clientTokenTtl(),
            List.of(),
            properties.clients());

    assertThatThrownBy(() -> authService(withoutUsers).refresh(refreshToken))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Account no longer exists");
  }

  private static AuthService authService(AuthProperties properties) {
    SecurityConfig config = new SecurityConfig();
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    return new AuthService(
        config.authenticationManager(config.userDetailsService(properties), encoder),
        TestAuth.tokenService(properties),
        properties);
  }
}
