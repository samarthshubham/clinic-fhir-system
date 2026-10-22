package io.github.samarthshubham.clinic.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class PatientAccessTest {

  private final PatientAccess access = new PatientAccess();

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void patientCanAccessOnlyTheirOwnRecord() {
    signIn(jwt(Role.PATIENT, "p1"));

    assertThat(access.isSelf("p1")).isTrue();
    assertThat(access.isSelf("p2")).isFalse();
    assertThat(access.isSelf(null)).isFalse();
  }

  @Test
  void staffAreNeverTreatedAsTheOwner() {
    signIn(jwt(Role.CLINICIAN, "p1"));

    assertThat(access.isSelf("p1")).isFalse();
  }

  @Test
  void nonJwtOrMissingAuthenticationIsNotTheOwner() {
    assertThat(access.isSelf("p1")).isFalse();

    signIn(
        UsernamePasswordAuthenticationToken.authenticated(
            "tony", null, RolePermissions.authoritiesFor(List.of(Role.PATIENT))));

    assertThat(access.isSelf("p1")).isFalse();
  }

  private static JwtAuthenticationToken jwt(Role role, String patientId) {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "HS256")
            .subject("tony")
            .claim("roles", List.of(role.name()))
            .claim("patient_id", patientId)
            .build();
    return new JwtAuthenticationToken(jwt, RolePermissions.authoritiesFor(List.of(role)));
  }

  private static void signIn(Authentication authentication) {
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}
