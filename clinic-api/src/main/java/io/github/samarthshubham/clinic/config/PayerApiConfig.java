package io.github.samarthshubham.clinic.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(PayerApiProperties.class)
public class PayerApiConfig {

  @Bean
  RestClient payerApiRestClient(RestClient.Builder builder, PayerApiProperties properties) {
    if (properties.clientSecret() == null || properties.clientSecret().isBlank()) {
      throw new IllegalStateException(
          "clinic.payer.client-secret (env CLINIC_PAYER_CLIENT_SECRET) must be set");
    }
    return payerApiRestClient(builder, properties.baseUrl(), properties.timeout());
  }

  public static RestClient payerApiRestClient(
      RestClient.Builder builder, String baseUrl, Duration timeout) {
    return builder.baseUrl(baseUrl).requestFactory(requestFactory(timeout)).build();
  }

  private static ClientHttpRequestFactory requestFactory(Duration timeout) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
    factory.setReadTimeout(timeout);
    return factory;
  }
}
