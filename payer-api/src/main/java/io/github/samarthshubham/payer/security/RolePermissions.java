package io.github.samarthshubham.payer.security;

import static io.github.samarthshubham.payer.security.Permission.MEMBER_READ;
import static io.github.samarthshubham.payer.security.Permission.PRIOR_AUTH_DECIDE;
import static io.github.samarthshubham.payer.security.Permission.PRIOR_AUTH_READ;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class RolePermissions {

  private static final Map<Role, Set<Permission>> PERMISSIONS =
      Map.of(
          Role.UM_REVIEWER,
          EnumSet.of(MEMBER_READ, PRIOR_AUTH_READ, PRIOR_AUTH_DECIDE),
          Role.PAYER_ADMIN,
          EnumSet.of(MEMBER_READ, PRIOR_AUTH_READ));

  private RolePermissions() {}

  public static Set<Permission> permissionsFor(Role role) {
    return Set.copyOf(PERMISSIONS.get(role));
  }

  public static Set<GrantedAuthority> authoritiesFor(Collection<Role> roles) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    for (Role role : roles) {
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
      authorities.addAll(PERMISSIONS.get(role));
    }
    return authorities;
  }
}
