package io.github.samarthshubham.payer.interop;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.RestfulServer;
import java.util.List;
import java.util.stream.Stream;

public class PayerFhirServer extends RestfulServer {

  public PayerFhirServer(
      FhirContext fhirContext,
      List<String> implementationGuides,
      List<IResourceProvider> resourceProviders,
      List<OperationScope> operationScopes,
      PlanNetDirectory directory) {
    super(fhirContext);
    setServerName("Praxis Health Plan payer-api");
    setDefaultResponseEncoding(EncodingEnum.JSON);
    setResourceProviders(
        Stream.concat(resourceProviders.stream(), directory.providers().stream()).toList());
    registerInterceptor(new ImplementationGuideCapabilityInterceptor(implementationGuides));
    registerInterceptor(new SmartCapabilityInterceptor());
    registerInterceptor(directory);
    registerInterceptor(new ScopeInterceptor(fhirContext, operationScopes, directory));
  }
}
