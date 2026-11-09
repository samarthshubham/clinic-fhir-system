package io.github.samarthshubham.payer.security;

import org.springframework.security.core.GrantedAuthority;

public enum Permission implements GrantedAuthority {
  MEMBER_READ("member:read"),
  PRIOR_AUTH_READ("priorauth:read"),
  PRIOR_AUTH_DECIDE("priorauth:decide");

  private final String authority;

  Permission(String authority) {
    this.authority = authority;
  }

  @Override
  public String getAuthority() {
    return authority;
  }
}
