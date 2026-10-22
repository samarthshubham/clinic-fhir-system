package io.github.samarthshubham.clinic.service;

import io.github.samarthshubham.clinic.config.AuthProperties;
import io.github.samarthshubham.clinic.dto.AuthUser;
import io.github.samarthshubham.clinic.dto.LoginRequest;
import io.github.samarthshubham.clinic.dto.PractitionerResponse;
import io.github.samarthshubham.clinic.dto.TokenResponse;
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

  public AuthService(
      AuthenticationManager authenticationManager,
      TokenService tokenService,
      AuthProperties authProperties,
      PractitionerService practitionerService) {
    this.authenticationManager = authenticationManager;
    this.tokenService = tokenService;
    this.authProperties = authProperties;
    this.practitionerService = practitionerService;
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
    Optional<PractitionerResponse> practitioner = practitionerService.findByUsername(username);
    String name =
        practitioner
            .map(p -> p.firstName() + " " + p.lastName())
            .orElse(account.firstName() + " " + account.lastName());
    String title = practitioner.map(PractitionerResponse::specialty).orElse(account.title());
    return new AuthUser(
        username,
        name,
        title,
        practitioner.map(PractitionerResponse::id).orElse(null),
        List.of(account.role()));
  }
}
