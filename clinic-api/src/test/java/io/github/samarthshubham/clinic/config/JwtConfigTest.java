package io.github.samarthshubham.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.clinic.security.Role;
import io.github.samarthshubham.clinic.support.TestAuth;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtConfigTest {

  private final JwtConfig config = new JwtConfig();

  @Test
  void rejectsSecretsTooShortForHs256() {
    AuthProperties weak =
        new AuthProperties("short", "clinic-api", Duration.ZERO, Duration.ZERO, List.of());

    assertThatThrownBy(() -> config.jwtSigningKey(weak))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least 32 bytes");
  }

  @Test
  void converterExpandsRolesClaimIntoAuthorities() {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "HS256")
            .subject("frontdesk")
            .claim("roles", List.of(Role.FRONT_DESK.name()))
            .build();

    assertThat(config.jwtAuthenticationConverter().convert(jwt).getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .contains("ROLE_FRONT_DESK", "patient:write")
        .doesNotContain("vitals:write");
  }

  @Test
  void converterGrantsNothingWithoutRolesClaim() {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject("nobody").build();

    assertThat(config.jwtAuthenticationConverter().convert(jwt).getAuthorities()).isEmpty();
  }

  @Test
  void signingKeyUsesConfiguredSecret() {
    assertThat(config.jwtSigningKey(TestAuth.properties()).getEncoded())
        .isEqualTo(TestAuth.KEY.getEncoded());
  }
}
