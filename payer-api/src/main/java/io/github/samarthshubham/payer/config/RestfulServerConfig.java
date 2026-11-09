package io.github.samarthshubham.payer.config;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.payer.interop.PayerFhirServer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestfulServerConfig {

  @Bean
  public ServletRegistrationBean<PayerFhirServer> fhirServlet(
      FhirContext fhirContext, ImplementationGuideProperties implementationGuides) {
    ServletRegistrationBean<PayerFhirServer> registration =
        new ServletRegistrationBean<>(
            new PayerFhirServer(fhirContext, implementationGuides.canonicals()), "/fhir/*");
    registration.setName("fhirServlet");
    registration.setLoadOnStartup(1);
    return registration;
  }
}
