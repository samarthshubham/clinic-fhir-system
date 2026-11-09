package io.github.samarthshubham.payer.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class RolePermissionsTest {

  @Test
  void onlyReviewersDecidePriorAuths() {
    assertThat(RolePermissions.permissionsFor(Role.UM_REVIEWER))
        .containsExactlyInAnyOrder(
            Permission.MEMBER_READ, Permission.PRIOR_AUTH_READ, Permission.PRIOR_AUTH_DECIDE);
    assertThat(RolePermissions.permissionsFor(Role.PAYER_ADMIN))
        .containsExactlyInAnyOrder(Permission.MEMBER_READ, Permission.PRIOR_AUTH_READ);
  }

  @Test
  void authoritiesIncludeTheRoleAndItsPermissions() {
    assertThat(RolePermissions.authoritiesFor(List.of(Role.PAYER_ADMIN)))
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_PAYER_ADMIN", "member:read", "priorauth:read");
  }
}
