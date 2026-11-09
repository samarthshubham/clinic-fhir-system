package io.github.samarthshubham.payer.interop;

import io.github.samarthshubham.payer.security.SmartPermission;

/**
 * SMART scopes cover REST interactions only, so each extended operation declares the resource type
 * and permission a token needs to call it. Operations without one are denied.
 */
public record OperationScope(String operation, String resourceType, SmartPermission permission) {}
