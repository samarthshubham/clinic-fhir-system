package io.github.samarthshubham.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.AuthProperties;
import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.AuthUser;
import io.github.samarthshubham.clinic.dto.LoginRequest;
import io.github.samarthshubham.clinic.dto.PractitionerResponse;
import io.github.samarthshubham.clinic.dto.TokenResponse;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.support.TestAuth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

  private final AuthProperties properties = TestAuth.properties();
  private final TokenService tokenService = TestAuth.tokenService(properties);
  private final PractitionerService practitionerService = mock(PractitionerService.class);
  private AuthService authService;

  @BeforeEach
  void setUp() {
    SecurityConfig securityConfig = new SecurityConfig();
    PasswordEncoder passwordEncoder = securityConfig.passwordEncoder();
    authService =
        new AuthService(
            securityConfig.authenticationManager(
                securityConfig.userDetailsService(properties), passwordEncoder),
            tokenService,
            properties,
            practitionerService);
    when(practitionerService.findByUsername("clinician"))
        .thenReturn(
            Optional.of(
                new PractitionerResponse(
                    "user-clinician", "Anika", "Patel", "Internal Medicine", null, null)));
  }

  @Test
  void loginWithCorrectPasswordReturnsTokensForThePractitioner() {
    TokenResponse tokens = authService.login(new LoginRequest("clinician", TestAuth.PASSWORD));

    assertThat(tokens.user())
        .isEqualTo(
            new AuthUser(
                "clinician",
                "Anika Patel",
                "Internal Medicine",
                "user-clinician",
                List.of(Role.CLINICIAN)));
    assertThat(TestAuth.accessTokenDecoder().decode(tokens.accessToken()).getSubject())
        .isEqualTo("clinician");
  }

  @Test
  void loginFallsBackToConfiguredNameWithoutPractitioner() {
    when(practitionerService.findByUsername("frontdesk")).thenReturn(Optional.empty());

    TokenResponse tokens = authService.login(new LoginRequest("frontdesk", TestAuth.PASSWORD));

    assertThat(tokens.user())
        .isEqualTo(
            new AuthUser("frontdesk", "Jordan Lee", "Front desk", null, List.of(Role.FRONT_DESK)));
  }

  @Test
  void wrongPasswordAndUnknownUserFailTheSameWay() {
    assertThatThrownBy(() -> authService.login(new LoginRequest("clinician", "wrong")))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid username or password");
    assertThatThrownBy(() -> authService.login(new LoginRequest("nobody", TestAuth.PASSWORD)))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid username or password");
  }

  @Test
  void refreshIssuesNewTokensForTheSameUser() {
    TokenResponse login = authService.login(new LoginRequest("clinician", TestAuth.PASSWORD));

    TokenResponse refreshed = authService.refresh(login.refreshToken());

    assertThat(refreshed.user()).isEqualTo(login.user());
    assertThat(TestAuth.accessTokenDecoder().decode(refreshed.accessToken()).getSubject())
        .isEqualTo("clinician");
  }

  @Test
  void refreshFailsWhenAccountWasRemoved() {
    String orphanRefreshToken =
        tokenService
            .issueTokens(new AuthUser("ghost", "Ghost", null, null, List.of(Role.ADMIN)))
            .refreshToken();

    assertThatThrownBy(() -> authService.refresh(orphanRefreshToken))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Account no longer exists");
  }
}
