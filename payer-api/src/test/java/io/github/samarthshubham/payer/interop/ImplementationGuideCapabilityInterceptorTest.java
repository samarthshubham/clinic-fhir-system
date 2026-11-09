package io.github.samarthshubham.payer.interop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.junit.jupiter.api.Test;

class ImplementationGuideCapabilityInterceptorTest {

  private static final String PAS =
      "http://hl7.org/fhir/us/davinci-pas/ImplementationGuide/hl7.fhir.us.davinci-pas|2.2.1";
  private static final String CRD =
      "http://hl7.org/fhir/us/davinci-crd/ImplementationGuide/hl7.fhir.us.davinci-crd|2.2.1";

  private final ImplementationGuideCapabilityInterceptor interceptor =
      new ImplementationGuideCapabilityInterceptor(List.of(PAS, CRD));

  @Test
  void listsTheConfiguredImplementationGuides() {
    CapabilityStatement capabilityStatement = new CapabilityStatement();

    interceptor.listImplementationGuides(capabilityStatement);

    assertThat(canonicals(capabilityStatement)).containsExactly(PAS, CRD);
  }

  @Test
  void replacesRatherThanAppendsWhenCalledAgain() {
    CapabilityStatement capabilityStatement = new CapabilityStatement();
    capabilityStatement.addImplementationGuide(
        "http://example.org/ImplementationGuide/other|1.0.0");

    interceptor.listImplementationGuides(capabilityStatement);
    interceptor.listImplementationGuides(capabilityStatement);

    assertThat(canonicals(capabilityStatement)).containsExactly(PAS, CRD);
  }

  private static List<String> canonicals(CapabilityStatement capabilityStatement) {
    return capabilityStatement.getImplementationGuide().stream()
        .map(CanonicalType::getValue)
        .toList();
  }
}
