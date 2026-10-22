package io.github.samarthshubham.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.clinic.dto.AuthUser;
import io.github.samarthshubham.clinic.dto.TokenResponse;
import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.support.TestAuth;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class TokenServiceTest {

  private final TokenService tokenService = TestAuth.tokenService(TestAuth.properties());

  @Test
  void accessTokenCarriesIdentityRolesAndExpiry() {
    TokenResponse tokens = tokenService.issueTokens(TestAuth.user(Role.CLINICIAN));

    Jwt jwt = TestAuth.accessTokenDecoder().decode(tokens.accessToken());

    assertThat(jwt.getSubject()).isEqualTo("clinician");
    assertThat(jwt.getClaimAsString("iss")).isEqualTo(TestAuth.ISSUER);
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CLINICIAN");
    assertThat(jwt.getClaimAsString("name")).isEqualTo("Test CLINICIAN");
    assertThat(jwt.getClaimAsString("practitioner_id")).isEqualTo("user-CLINICIAN");
    assertThat(jwt.getClaimAsString("token_type")).isEqualTo("access");
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofMinutes(15));
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    assertThat(tokens.tokenType()).isEqualTo("Bearer");
    assertThat(tokens.expiresIn()).isEqualTo(900);
  }

  @Test
  void accessTokenOmitsPractitionerIdWhenUnknown() {
    AuthUser user = new AuthUser("ghost", "Ghost", null, List.of(Role.FRONT_DESK));

    Jwt jwt = TestAuth.accessTokenDecoder().decode(tokenService.issueTokens(user).accessToken());

    assertThat(jwt.hasClaim("practitioner_id")).isFalse();
  }

  @Test
  void refreshTokenYieldsUsername() {
    TokenResponse tokens = tokenService.issueTokens(TestAuth.user(Role.ADMIN));

    assertThat(tokenService.usernameFromRefreshToken(tokens.refreshToken())).isEqualTo("admin");
  }

  @Test
  void refreshTokenIsRejectedAsAccessToken() {
    TokenResponse tokens = tokenService.issueTokens(TestAuth.user(Role.ADMIN));

    assertThatThrownBy(() -> TestAuth.accessTokenDecoder().decode(tokens.refreshToken()))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("token_type");
  }

  @Test
  void accessTokenIsRejectedAsRefreshToken() {
    TokenResponse tokens = tokenService.issueTokens(TestAuth.user(Role.ADMIN));

    assertThatThrownBy(() -> tokenService.usernameFromRefreshToken(tokens.accessToken()))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid or expired refresh token");
  }

  @Test
  void tamperedTokenFailsSignatureCheck() {
    String token = tokenService.issueTokens(TestAuth.user(Role.FRONT_DESK)).accessToken();
    String[] parts = token.split("\\.");
    String forgedPayload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                    .replace("FRONT_DESK", "ADMIN")
                    .getBytes(StandardCharsets.UTF_8));
    String forged = parts[0] + "." + forgedPayload + "." + parts[2];

    assertThatThrownBy(() -> TestAuth.accessTokenDecoder().decode(forged))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void expiredTokensAreRejected() {
    Instant twentyMinutesAgo = Instant.now().minus(Duration.ofMinutes(20));

    String expiredAccess = TestAuth.signedToken("access", twentyMinutesAgo, Duration.ofMinutes(15));
    String expiredRefresh =
        TestAuth.signedToken("refresh", twentyMinutesAgo, Duration.ofMinutes(15));

    assertThatThrownBy(() -> TestAuth.accessTokenDecoder().decode(expiredAccess))
        .isInstanceOf(JwtException.class)
        .hasMessageContaining("expired");
    assertThatThrownBy(() -> tokenService.usernameFromRefreshToken(expiredRefresh))
        .isInstanceOf(BadCredentialsException.class);
  }
}
