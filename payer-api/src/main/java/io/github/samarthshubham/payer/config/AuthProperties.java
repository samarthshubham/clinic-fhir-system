package io.github.samarthshubham.payer.config;

import io.github.samarthshubham.payer.security.Role;
import io.github.samarthshubham.payer.security.SmartScope;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payer.auth")
public record AuthProperties(
    String jwtSecret,
    String issuer,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    Duration clientTokenTtl,
    List<User> users,
    List<Client> clients) {

  public AuthProperties {
    users = users == null ? List.of() : List.copyOf(users);
    clients = clients == null ? List.of() : List.copyOf(clients);
    for (Client client : clients) {
      for (String scope : client.scopes()) {
        boolean systemScope =
            SmartScope.parse(scope)
                .filter(parsed -> parsed.context() == SmartScope.Context.SYSTEM)
                .isPresent();
        if (!systemScope) {
          throw new IllegalArgumentException(
              "payer.auth.clients: client '"
                  + client.clientId()
                  + "' has '"
                  + scope
                  + "', which is not a SMART v2 system scope");
        }
      }
    }
  }

  public Optional<User> findUser(String username) {
    return users.stream().filter(user -> user.username().equals(username)).findFirst();
  }

  public Optional<Client> findClient(String clientId) {
    return clients.stream().filter(client -> client.clientId().equals(clientId)).findFirst();
  }

  public record User(
      String username,
      String passwordHash,
      Role role,
      String firstName,
      String lastName,
      String title) {}

  public record Client(String clientId, String clientSecretHash, List<String> scopes) {

    public Client {
      scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }
  }
}
