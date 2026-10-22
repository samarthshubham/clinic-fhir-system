package io.github.samarthshubham.clinic.security;

import static io.github.samarthshubham.clinic.security.Permission.APPOINTMENT_READ;
import static io.github.samarthshubham.clinic.security.Permission.APPOINTMENT_WRITE;
import static io.github.samarthshubham.clinic.security.Permission.PATIENT_READ;
import static io.github.samarthshubham.clinic.security.Permission.PATIENT_WRITE;
import static io.github.samarthshubham.clinic.security.Permission.PRACTITIONER_READ;
import static io.github.samarthshubham.clinic.security.Permission.PRACTITIONER_WRITE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class RolePermissionsTest {

  @Test
  void adminHasEveryPermission() {
    assertThat(RolePermissions.permissionsFor(Role.ADMIN))
        .containsExactlyInAnyOrder(Permission.values());
  }

  @Test
  void clinicianHasEverythingExceptManagingPractitioners() {
    assertThat(RolePermissions.permissionsFor(Role.CLINICIAN))
        .doesNotContain(PRACTITIONER_WRITE)
        .hasSize(Permission.values().length - 1);
  }

  @Test
  void frontDeskIsLimitedToRegistrationAndScheduling() {
    assertThat(RolePermissions.permissionsFor(Role.FRONT_DESK))
        .containsExactlyInAnyOrder(
            PATIENT_READ, PATIENT_WRITE, APPOINTMENT_READ, APPOINTMENT_WRITE, PRACTITIONER_READ);
  }

  @Test
  void authoritiesIncludeRoleAndItsPermissions() {
    assertThat(RolePermissions.authoritiesFor(List.of(Role.FRONT_DESK)))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder(
            "ROLE_FRONT_DESK",
            "patient:read",
            "patient:write",
            "appointment:read",
            "appointment:write",
            "practitioner:read");
  }

  @Test
  void patientsGetNoGeneralPermissionsOnlyTheirRole() {
    assertThat(RolePermissions.permissionsFor(Role.PATIENT)).isEmpty();
    assertThat(RolePermissions.authoritiesFor(List.of(Role.PATIENT)))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_PATIENT");
  }
}
