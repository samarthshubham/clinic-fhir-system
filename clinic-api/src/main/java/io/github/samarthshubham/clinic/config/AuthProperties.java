package io.github.samarthshubham.clinic.config;

import io.github.samarthshubham.clinic.security.Role;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinic.auth")
public record AuthProperties(
    String jwtSecret,
    String issuer,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    List<User> users) {

  public AuthProperties {
    users = users == null ? List.of() : List.copyOf(users);
  }

  public Optional<User> findUser(String username) {
    return users.stream().filter(user -> user.username().equals(username)).findFirst();
  }

  public record User(
      String username,
      String passwordHash,
      Role role,
      String firstName,
      String lastName,
      String title,
      String patientId) {}
}
