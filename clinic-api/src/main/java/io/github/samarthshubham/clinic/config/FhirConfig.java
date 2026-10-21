package io.github.samarthshubham.clinic.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ClinicalThresholds.class)
public class FhirConfig {

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
