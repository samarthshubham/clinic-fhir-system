package io.github.samarthshubham.clinic.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(ClinicalThresholds.class)
public class FhirConfig implements WebMvcConfigurer {

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

  @Override
  public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    converters.add(0, new FhirHttpMessageConverter(fhirContext()));
  }
}
