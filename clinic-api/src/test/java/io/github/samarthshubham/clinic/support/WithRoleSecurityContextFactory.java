package io.github.samarthshubham.clinic.support;

import io.github.samarthshubham.clinic.security.RolePermissions;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class WithRoleSecurityContextFactory implements WithSecurityContextFactory<WithRole> {

  @Override
  public SecurityContext createSecurityContext(WithRole withRole) {
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            withRole.value().name().toLowerCase(),
            null,
            RolePermissions.authoritiesFor(List.of(withRole.value()))));
    return context;
  }
}
