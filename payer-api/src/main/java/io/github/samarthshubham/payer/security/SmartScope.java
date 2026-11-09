package io.github.samarthshubham.payer.security;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record SmartScope(Context context, String resourceType, Set<SmartPermission> permissions) {

  public static final String ANY_RESOURCE = "*";

  private static final Pattern V2_SCOPE =
      Pattern.compile("^(patient|user|system)/([A-Z][A-Za-z]*|\\*)\\.(c?r?u?d?s?)$");

  public enum Context {
    PATIENT,
    USER,
    SYSTEM
  }

  public SmartScope {
    permissions = Set.copyOf(permissions);
  }

  public static Optional<SmartScope> parse(String scope) {
    Matcher matcher = V2_SCOPE.matcher(scope);
    if (!matcher.matches() || matcher.group(3).isEmpty()) {
      return Optional.empty();
    }
    Set<SmartPermission> permissions = EnumSet.noneOf(SmartPermission.class);
    for (SmartPermission permission : SmartPermission.values()) {
      if (matcher.group(3).indexOf(permission.code()) >= 0) {
        permissions.add(permission);
      }
    }
    return Optional.of(
        new SmartScope(
            Context.valueOf(matcher.group(1).toUpperCase()), matcher.group(2), permissions));
  }

  public static List<SmartScope> parseAll(String spaceDelimitedScopes) {
    if (spaceDelimitedScopes == null) {
      return List.of();
    }
    return Arrays.stream(spaceDelimitedScopes.trim().split("\\s+"))
        .map(SmartScope::parse)
        .flatMap(Optional::stream)
        .toList();
  }

  public boolean anyResource() {
    return ANY_RESOURCE.equals(resourceType);
  }

  public boolean grants(String type, SmartPermission permission) {
    return (anyResource() || resourceType.equals(type)) && permissions.contains(permission);
  }
}
