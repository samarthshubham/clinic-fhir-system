package io.github.samarthshubham.payer.service;

import io.github.samarthshubham.payer.config.AuthProperties;
import io.github.samarthshubham.payer.dto.OAuthTokenResponse;
import io.github.samarthshubham.payer.exception.OAuthException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ClientCredentialsService {

  public static final String CLIENT_CREDENTIALS = "client_credentials";

  private final AuthProperties authProperties;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;
  private final String unknownClientHash;

  public ClientCredentialsService(
      AuthProperties authProperties, PasswordEncoder passwordEncoder, TokenService tokenService) {
    this.authProperties = authProperties;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    this.unknownClientHash = passwordEncoder.encode("unknown-client");
  }

  public OAuthTokenResponse issueToken(
      String grantType, String clientId, String clientSecret, String requestedScope) {
    if (grantType == null || grantType.isBlank()) {
      throw OAuthException.invalidRequest("grant_type is required");
    }
    if (!CLIENT_CREDENTIALS.equals(grantType)) {
      throw OAuthException.unsupportedGrantType();
    }
    Optional<AuthProperties.Client> registered = authProperties.findClient(clientId);
    String hash = registered.map(AuthProperties.Client::clientSecretHash).orElse(unknownClientHash);
    boolean secretMatches = clientSecret != null && passwordEncoder.matches(clientSecret, hash);
    AuthProperties.Client client =
        registered.filter(found -> secretMatches).orElseThrow(OAuthException::invalidClient);
    return tokenService.issueSystemToken(client.clientId(), grantedScopes(client, requestedScope));
  }

  private static List<String> grantedScopes(AuthProperties.Client client, String requestedScope) {
    if (requestedScope == null || requestedScope.isBlank()) {
      return client.scopes();
    }
    List<String> requested = Arrays.stream(requestedScope.trim().split("\\s+")).distinct().toList();
    List<String> notRegistered =
        requested.stream().filter(scope -> !client.scopes().contains(scope)).toList();
    if (!notRegistered.isEmpty()) {
      throw OAuthException.invalidScope(
          "Scope not registered for this client: " + String.join(" ", notRegistered));
    }
    return requested;
  }
}
