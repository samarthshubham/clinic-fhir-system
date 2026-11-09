package io.github.samarthshubham.payer.interop;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import java.util.List;

public class PayerFhirServer extends RestfulServer {

  public PayerFhirServer(
      FhirContext fhirContext,
      List<String> implementationGuides,
      List<IResourceProvider> resourceProviders,
      List<OperationScope> operationScopes) {
    super(fhirContext);
    setServerName("Praxis Health Plan payer-api");
    setDefaultResponseEncoding(EncodingEnum.JSON);
    setResourceProviders(resourceProviders);
    registerInterceptor(new ImplementationGuideCapabilityInterceptor(implementationGuides));
    registerInterceptor(new SmartCapabilityInterceptor());
    registerInterceptor(new ScopeInterceptor(fhirContext, operationScopes));
  }
}
