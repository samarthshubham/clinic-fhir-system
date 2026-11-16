package io.github.samarthshubham.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class PayerApiConfigTest {

  private final PayerApiConfig config = new PayerApiConfig();

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void aMissingClientSecretStopsStartup(String secret) {
    PayerApiProperties properties =
        new PayerApiProperties(
            "http://localhost:9095",
            "clinic-api",
            secret,
            "system/Coverage.r",
            Duration.ofSeconds(2));

    assertThatThrownBy(() -> config.payerApiRestClient(RestClient.builder(), properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CLINIC_PAYER_CLIENT_SECRET");
  }

  @Test
  void aConfiguredClientSecretBuildsTheClient() {
    PayerApiProperties properties =
        new PayerApiProperties(
            "http://localhost:9095",
            "clinic-api",
            "secret",
            "system/Coverage.r",
            Duration.ofSeconds(2));

    assertThat(config.payerApiRestClient(RestClient.builder(), properties)).isNotNull();
  }
}
