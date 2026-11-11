package io.github.samarthshubham.payer.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FhirConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }

  @Bean
  public IGenericClient fhirClient(
      FhirContext fhirContext, @Value("${fhir.server.url}") String serverUrl) {
    IGenericClient client = fhirContext.newRestfulGenericClient(serverUrl);
    LoggingInterceptor logging = new LoggingInterceptor();
    logging.setLogRequestSummary(true);
    logging.setLogResponseSummary(true);
    client.registerInterceptor(logging);
    return client;
  }
}
