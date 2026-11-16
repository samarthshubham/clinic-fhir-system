package io.github.samarthshubham.clinic.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clinic.payer")
public record PayerApiProperties(
    String baseUrl, String clientId, String clientSecret, String scope, Duration timeout) {}
