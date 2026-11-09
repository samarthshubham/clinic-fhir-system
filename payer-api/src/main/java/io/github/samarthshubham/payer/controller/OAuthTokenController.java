package io.github.samarthshubham.payer.controller;

import io.github.samarthshubham.payer.dto.OAuthTokenResponse;
import io.github.samarthshubham.payer.exception.OAuthException;
import io.github.samarthshubham.payer.service.ClientCredentialsService;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuthTokenController {

  private static final String BASIC_PREFIX = "basic ";

  private final ClientCredentialsService clientCredentialsService;

  public OAuthTokenController(ClientCredentialsService clientCredentialsService) {
    this.clientCredentialsService = clientCredentialsService;
  }

  @PostMapping(path = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<OAuthTokenResponse> token(
      @RequestParam MultiValueMap<String, String> form,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    ClientCredentials client = clientCredentials(authorization, form);
    OAuthTokenResponse token =
        clientCredentialsService.issueToken(
            single(form, "grant_type"), client.id(), client.secret(), single(form, "scope"));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(token);
  }

  private static ClientCredentials clientCredentials(
      String authorization, MultiValueMap<String, String> form) {
    if (authorization == null || !authorization.toLowerCase().startsWith(BASIC_PREFIX)) {
      return new ClientCredentials(single(form, "client_id"), single(form, "client_secret"));
    }
    if (form.containsKey("client_secret")) {
      throw OAuthException.invalidRequest("Use only one client authentication method");
    }
    try {
      String decoded =
          new String(
              Base64.getDecoder().decode(authorization.substring(BASIC_PREFIX.length()).trim()),
              StandardCharsets.UTF_8);
      int colon = decoded.indexOf(':');
      if (colon < 0) {
        throw OAuthException.invalidClient();
      }
      return new ClientCredentials(
          URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8),
          URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      throw OAuthException.invalidClient();
    }
  }

  private static String single(MultiValueMap<String, String> form, String name) {
    List<String> values = form.get(name);
    if (values == null) {
      return null;
    }
    if (values.size() > 1) {
      throw OAuthException.invalidRequest(name + " must not be repeated");
    }
    return values.getFirst();
  }

  private record ClientCredentials(String id, String secret) {}
}
