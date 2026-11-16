package io.github.samarthshubham.clinic.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.samarthshubham.clinic.config.PayerApiProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayerTokenClientTest {

  private static final String PAYER = "http://payer.test";

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-16T18:00:00Z"));
  private MockRestServiceServer server;
  private PayerTokenClient tokens;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(PAYER);
    server = MockRestServiceServer.bindTo(builder).build();
    tokens =
        new PayerTokenClient(
            builder.build(),
            new PayerApiProperties(
                PAYER, "clinic-api", "test-secret", "system/Coverage.r", Duration.ofSeconds(2)),
            clock);
  }

  private void expectToken(String token) {
    server
        .expect(once(), requestTo(PAYER + "/oauth/token"))
        .andExpect(
            content()
                .formDataContains(
                    Map.of("grant_type", "client_credentials", "scope", "system/Coverage.r")))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"" + token + "\",\"token_type\":\"bearer\",\"expires_in\":300}",
                MediaType.APPLICATION_JSON));
  }

  @Test
  void theTokenIsReusedUntilItExpires() {
    expectToken("first");
    expectToken("second");

    assertThat(tokens.accessToken()).isEqualTo("first");
    clock.advance(Duration.ofSeconds(300).minus(PayerTokenClient.EXPIRY_MARGIN).minusSeconds(1));
    assertThat(tokens.accessToken()).isEqualTo("first");
    clock.advance(Duration.ofSeconds(1));
    assertThat(tokens.accessToken()).isEqualTo("second");
    server.verify();
  }

  @Test
  void anEvictedTokenIsRequestedAgain() {
    expectToken("first");
    expectToken("second");

    tokens.accessToken();
    tokens.evict();

    assertThat(tokens.accessToken()).isEqualTo("second");
  }

  @Test
  void aResponseWithoutAnAccessTokenIsAnError() {
    server
        .expect(once(), requestTo(PAYER + "/oauth/token"))
        .andRespond(withSuccess("{\"expires_in\":300}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(tokens::accessToken)
        .isInstanceOf(PayerApiException.class)
        .hasMessage("payer-api /oauth/token returned no access token");
  }

  private static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
