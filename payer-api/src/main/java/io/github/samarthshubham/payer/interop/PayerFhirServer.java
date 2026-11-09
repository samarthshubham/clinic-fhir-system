package io.github.samarthshubham.payer.interop;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import java.util.List;

public class PayerFhirServer extends RestfulServer {

  public PayerFhirServer(FhirContext fhirContext, List<String> implementationGuides) {
    super(fhirContext);
    setServerName("Praxis Health Plan payer-api");
    setDefaultResponseEncoding(EncodingEnum.JSON);
    registerInterceptor(new ImplementationGuideCapabilityInterceptor(implementationGuides));
  }
}
