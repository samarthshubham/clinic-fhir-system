package io.github.samarthshubham.payer.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payer.crd")
public record CrdProperties(
    String payerName, List<PayerIdentifier> payerIdentifiers, List<String> prefetchFhirServers) {

  public CrdProperties {
    payerIdentifiers = payerIdentifiers == null ? List.of() : List.copyOf(payerIdentifiers);
    prefetchFhirServers =
        prefetchFhirServers == null
            ? List.of()
            : prefetchFhirServers.stream().map(CrdProperties::withoutTrailingSlash).toList();
  }

  public boolean isOurPayer(String system, String value) {
    return payerIdentifiers.stream()
        .anyMatch(
            identifier -> identifier.system().equals(system) && identifier.value().equals(value));
  }

  public boolean mayFetchPrefetchFrom(String fhirServer) {
    return fhirServer != null && prefetchFhirServers.contains(withoutTrailingSlash(fhirServer));
  }

  private static String withoutTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  public record PayerIdentifier(String system, String value) {}
}
