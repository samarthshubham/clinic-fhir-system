package io.github.samarthshubham.payer.service;

import io.github.samarthshubham.payer.config.AuthProperties;
import io.github.samarthshubham.payer.dto.AuthUser;
import io.github.samarthshubham.payer.dto.LoginRequest;
import io.github.samarthshubham.payer.dto.TokenResponse;
import java.util.List;
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

  public AuthService(
      AuthenticationManager authenticationManager,
      TokenService tokenService,
      AuthProperties authProperties) {
    this.authenticationManager = authenticationManager;
    this.tokenService = tokenService;
    this.authProperties = authProperties;
  }

  public TokenResponse login(LoginRequest request) {
    try {
      authenticationManager.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(
              request.username(), request.password()));
    } catch (AuthenticationException e) {
      throw new BadCredentialsException("Invalid username or password", e);
    }
    return tokenService.issueStaffTokens(authUser(request.username()));
  }

  public TokenResponse refresh(String refreshToken) {
    return tokenService.issueStaffTokens(
        authUser(tokenService.usernameFromRefreshToken(refreshToken)));
  }

  private AuthUser authUser(String username) {
    AuthProperties.User account =
        authProperties
            .findUser(username)
            .orElseThrow(() -> new BadCredentialsException("Account no longer exists"));
    return new AuthUser(
        username,
        account.firstName() + " " + account.lastName(),
        account.title(),
        List.of(account.role()));
  }
}
