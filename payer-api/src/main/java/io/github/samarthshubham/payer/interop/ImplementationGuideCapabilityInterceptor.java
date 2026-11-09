package io.github.samarthshubham.payer.interop;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CapabilityStatement;

@Interceptor
public class ImplementationGuideCapabilityInterceptor {

  private final List<String> implementationGuides;

  public ImplementationGuideCapabilityInterceptor(List<String> implementationGuides) {
    this.implementationGuides = List.copyOf(implementationGuides);
  }

  @Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
  public void listImplementationGuides(IBaseConformance capabilityStatement) {
    ((CapabilityStatement) capabilityStatement)
        .setImplementationGuide(implementationGuides.stream().map(CanonicalType::new).toList());
  }
}
