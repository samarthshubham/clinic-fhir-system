package io.github.samarthshubham.payer.interop;

import io.github.samarthshubham.payer.security.SmartPermission;

/**
 * SMART scopes cover REST interactions only, so each extended operation declares the resource type
 * and permission a token needs to call it. Operations without one are denied. A {@code systemOnly}
 * operation is denied to member tokens, because HAPI's compartment check does not look inside
 * resources nested in an operation's response.
 */
public record OperationScope(
    String operation, String resourceType, SmartPermission permission, boolean systemOnly) {

  public OperationScope(String operation, String resourceType, SmartPermission permission) {
    this(operation, resourceType, permission, false);
  }
}
