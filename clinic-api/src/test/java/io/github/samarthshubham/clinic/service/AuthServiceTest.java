package io.github.samarthshubham.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.AuthProperties;
import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.dto.AuthUser;
import io.github.samarthshubham.clinic.dto.LoginRequest;
import io.github.samarthshubham.clinic.dto.PatientResponse;
import io.github.samarthshubham.clinic.dto.PractitionerResponse;
import io.github.samarthshubham.clinic.dto.TokenResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
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
  private final PatientService patientService = mock(PatientService.class);
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
            practitionerService,
            patientService);
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
                null,
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
            new AuthUser(
                "frontdesk", "Jordan Lee", "Front desk", null, null, List.of(Role.FRONT_DESK)));
  }

  @Test
  void patientLoginUsesTheirPatientRecordAndCarriesTheirId() {
    when(patientService.getPatient(TestAuth.OWN_PATIENT_ID))
        .thenReturn(
            new PatientResponse(
                TestAuth.OWN_PATIENT_ID, "Anthony", "Stark", null, null, null, null, true));

    TokenResponse tokens = authService.login(new LoginRequest("tony", TestAuth.PASSWORD));

    assertThat(tokens.user())
        .isEqualTo(
            new AuthUser(
                "tony",
                "Anthony Stark",
                null,
                null,
                TestAuth.OWN_PATIENT_ID,
                List.of(Role.PATIENT)));
    assertThat(
            TestAuth.accessTokenDecoder()
                .decode(tokens.accessToken())
                .getClaimAsString("patient_id"))
        .isEqualTo(TestAuth.OWN_PATIENT_ID);
    verifyNoInteractions(practitionerService);
  }

  @Test
  void patientLoginFallsBackToConfiguredNameWhenRecordIsMissing() {
    when(patientService.getPatient(TestAuth.OWN_PATIENT_ID))
        .thenThrow(new ResourceNotFoundException("Patient", TestAuth.OWN_PATIENT_ID));

    TokenResponse tokens = authService.login(new LoginRequest("tony", TestAuth.PASSWORD));

    assertThat(tokens.user().name()).isEqualTo("Tony Stark");
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
            .issueTokens(new AuthUser("ghost", "Ghost", null, null, null, List.of(Role.ADMIN)))
            .refreshToken();

    assertThatThrownBy(() -> authService.refresh(orphanRefreshToken))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Account no longer exists");
  }
}
