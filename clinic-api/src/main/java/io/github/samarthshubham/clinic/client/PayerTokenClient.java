package io.github.samarthshubham.clinic.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.samarthshubham.clinic.config.PayerApiProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class PayerTokenClient {

  static final Duration EXPIRY_MARGIN = Duration.ofSeconds(10);

  private final RestClient payerApiRestClient;
  private final PayerApiProperties properties;
  private final Clock clock;
  private String accessToken;
  private Instant refreshAt = Instant.MIN;

  @Autowired
  public PayerTokenClient(RestClient payerApiRestClient, PayerApiProperties properties) {
    this(payerApiRestClient, properties, Clock.systemUTC());
  }

  PayerTokenClient(RestClient payerApiRestClient, PayerApiProperties properties, Clock clock) {
    this.payerApiRestClient = payerApiRestClient;
    this.properties = properties;
    this.clock = clock;
  }

  /** Returns the cached system token, asking payer-api's /oauth/token again once it expires. */
  public synchronized String accessToken() {
    if (accessToken == null || !clock.instant().isBefore(refreshAt)) {
      OAuthToken token = requestToken();
      accessToken = token.accessToken();
      refreshAt = clock.instant().plusSeconds(token.expiresIn()).minus(EXPIRY_MARGIN);
    }
    return accessToken;
  }

  public synchronized void evict() {
    accessToken = null;
  }

  private OAuthToken requestToken() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "client_credentials");
    form.add("scope", properties.scope());
    OAuthToken token =
        payerApiRestClient
            .post()
            .uri("/oauth/token")
            .headers(
                headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret()))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(OAuthToken.class);
    if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
      throw new PayerApiException("payer-api /oauth/token returned no access token");
    }
    return token;
  }

  record OAuthToken(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") long expiresIn) {}
}
