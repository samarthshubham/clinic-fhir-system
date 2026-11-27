package io.github.samarthshubham.payer.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.IResourceProvider;
import io.github.samarthshubham.payer.interop.ClaimSubmitProvider;
import io.github.samarthshubham.payer.interop.OperationScope;
import io.github.samarthshubham.payer.interop.PayerFhirServer;
import io.github.samarthshubham.payer.interop.PublicPayerCatalog;
import io.github.samarthshubham.payer.interop.QuestionnairePackageProvider;
import io.github.samarthshubham.payer.security.SmartPermission;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestfulServerConfig {

  @Bean
  public ServletRegistrationBean<PayerFhirServer> fhirServlet(
      FhirContext fhirContext,
      ImplementationGuideProperties implementationGuides,
      ObjectProvider<IResourceProvider> resourceProviders,
      ObjectProvider<OperationScope> operationScopes,
      PublicPayerCatalog directory) {
    ServletRegistrationBean<PayerFhirServer> registration =
        new ServletRegistrationBean<>(
            new PayerFhirServer(
                fhirContext,
                implementationGuides.canonicals(),
                resourceProviders.orderedStream().toList(),
                operationScopes.orderedStream().toList(),
                directory),
            "/fhir/*");
    registration.setName("fhirServlet");
    registration.setLoadOnStartup(1);
    return registration;
  }

  @Bean
  public OperationScope claimSubmitScope() {
    return new OperationScope(ClaimSubmitProvider.OPERATION, "Claim", SmartPermission.CREATE, true);
  }

  @Bean
  public OperationScope claimInquiryScope() {
    return new OperationScope(ClaimSubmitProvider.INQUIRE, "Claim", SmartPermission.READ, true);
  }

  @Bean
  public OperationScope questionnairePackageScope() {
    return new OperationScope(
        QuestionnairePackageProvider.OPERATION, "Questionnaire", SmartPermission.READ, true);
  }
}
