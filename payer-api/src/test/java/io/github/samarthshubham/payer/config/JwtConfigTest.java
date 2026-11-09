package io.github.samarthshubham.payer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.payer.support.TestAuth;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtConfigTest {

  private final JwtConfig jwtConfig = new JwtConfig();

  @Test
  void shortOrMissingSecretIsRejected() {
    assertThatThrownBy(() -> jwtConfig.jwtSigningKey(withSecret("too-short")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PAYER_JWT_SECRET");
    assertThatThrownBy(() -> jwtConfig.jwtSigningKey(withSecret(null)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rolesBecomeAuthoritiesAndScopesDoNot() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "HS256"),
            Map.of(
                JwtConfig.ROLES_CLAIM,
                List.of("UM_REVIEWER"),
                JwtConfig.SCOPE_CLAIM,
                "system/Claim.c"));

    assertThat(jwtConfig.jwtAuthenticationConverter().convert(jwt).getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .contains("ROLE_UM_REVIEWER", "priorauth:decide")
        .noneMatch(authority -> authority.startsWith("SCOPE_"));
  }

  @Test
  void tokenWithoutRolesHasNoAuthorities() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "HS256"),
            Map.of(JwtConfig.SCOPE_CLAIM, "system/Claim.c"));

    assertThat(jwtConfig.jwtAuthenticationConverter().convert(jwt).getAuthorities()).isEmpty();
  }

  private static AuthProperties withSecret(String secret) {
    AuthProperties properties = TestAuth.properties();
    return new AuthProperties(
        secret,
        properties.issuer(),
        properties.accessTokenTtl(),
        properties.refreshTokenTtl(),
        properties.clientTokenTtl(),
        properties.users(),
        properties.clients());
  }
}
