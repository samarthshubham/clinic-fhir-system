package io.github.samarthshubham.clinic.service;

import io.github.samarthshubham.clinic.config.AuthProperties;
import io.github.samarthshubham.clinic.dto.AuthUser;
import io.github.samarthshubham.clinic.dto.LoginRequest;
import io.github.samarthshubham.clinic.dto.PatientResponse;
import io.github.samarthshubham.clinic.dto.PractitionerResponse;
import io.github.samarthshubham.clinic.dto.TokenResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.security.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final AuthenticationManager authenticationManager;
  private final TokenService tokenService;
  private final AuthProperties authProperties;
  private final PractitionerService practitionerService;
  private final PatientService patientService;

  public AuthService(
      AuthenticationManager authenticationManager,
      TokenService tokenService,
      AuthProperties authProperties,
      PractitionerService practitionerService,
      PatientService patientService) {
    this.authenticationManager = authenticationManager;
    this.tokenService = tokenService;
    this.authProperties = authProperties;
    this.practitionerService = practitionerService;
    this.patientService = patientService;
  }

  public TokenResponse login(LoginRequest request) {
    try {
      authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(
              request.username(), request.password()));
    } catch (AuthenticationException e) {
      throw new BadCredentialsException("Invalid username or password", e);
    }
    return tokenService.issueTokens(authUser(request.username()));
  }

  public TokenResponse refresh(String refreshToken) {
    return tokenService.issueTokens(authUser(tokenService.usernameFromRefreshToken(refreshToken)));
  }

  private AuthUser authUser(String username) {
    AuthProperties.User account =
        authProperties
            .findUser(username)
            .orElseThrow(() -> new BadCredentialsException("Account no longer exists"));
    String configuredName = account.firstName() + " " + account.lastName();
    if (account.role() == Role.PATIENT) {
      return new AuthUser(
          username,
          patientName(account.patientId()).orElse(configuredName),
          account.title(),
          null,
          account.patientId(),
          List.of(account.role()));
    }
    Optional<PractitionerResponse> practitioner = practitionerService.findByUsername(username);
    return new AuthUser(
        username,
        practitioner.map(p -> p.firstName() + " " + p.lastName()).orElse(configuredName),
        practitioner.map(PractitionerResponse::specialty).orElse(account.title()),
        practitioner.map(PractitionerResponse::id).orElse(null),
        null,
        List.of(account.role()));
  }

  private Optional<String> patientName(String patientId) {
    try {
      PatientResponse patient = patientService.getPatient(patientId);
      return Optional.of(patient.firstName() + " " + patient.lastName());
    } catch (ResourceNotFoundException e) {
      return Optional.empty();
    }
  }
}
