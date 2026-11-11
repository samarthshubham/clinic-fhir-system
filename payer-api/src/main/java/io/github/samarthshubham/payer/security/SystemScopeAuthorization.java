package io.github.samarthshubham.payer.security;

import io.github.samarthshubham.payer.config.JwtConfig;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

public final class SystemScopeAuthorization {

  private SystemScopeAuthorization() {}

  public static AuthorizationManager<RequestAuthorizationContext> requires(
      String resourceType, SmartPermission permission) {
    return (authentication, context) ->
        new AuthorizationDecision(grants(authentication.get(), resourceType, permission));
  }

  static boolean grants(
      Authentication authentication, String resourceType, SmartPermission permission) {
    if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token)) {
      return false;
    }
    if (token.getClaimAsString(JwtConfig.PATIENT_CLAIM) != null) {
      return false;
    }
    return SmartScope.parseAll(token.getClaimAsString(JwtConfig.SCOPE_CLAIM)).stream()
        .anyMatch(
            scope ->
                scope.context() == SmartScope.Context.SYSTEM
                    && scope.grants(resourceType, permission));
  }
}
