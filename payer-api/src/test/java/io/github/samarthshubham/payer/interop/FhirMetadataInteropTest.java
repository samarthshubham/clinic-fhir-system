package io.github.samarthshubham.payer.interop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import java.util.List;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Enumerations.FHIRVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FhirMetadataInteropTest {

  private static final List<String> SUPPORTED_IGS =
      List.of(
          "http://hl7.org/fhir/us/core/ImplementationGuide/hl7.fhir.us.core|7.0.0",
          "http://hl7.org/fhir/us/davinci-hrex/ImplementationGuide/hl7.fhir.us.davinci-hrex|1.2.0",
          "http://hl7.org/fhir/us/davinci-crd/ImplementationGuide/hl7.fhir.us.davinci-crd|2.2.1",
          "http://hl7.org/fhir/us/davinci-dtr/ImplementationGuide/hl7.fhir.us.davinci-dtr|2.2.0",
          "http://hl7.org/fhir/us/davinci-pas/ImplementationGuide/hl7.fhir.us.davinci-pas|2.2.1",
          "http://hl7.org/fhir/us/davinci-cdex/ImplementationGuide/hl7.fhir.us.davinci-cdex|2.1.0",
          "http://hl7.org/fhir/us/davinci-pdex/ImplementationGuide/hl7.fhir.us.davinci-pdex|2.2.0",
          "http://hl7.org/fhir/us/carin-bb/ImplementationGuide/hl7.fhir.us.carin-bb|2.2.0",
          "http://hl7.org/fhir/us/davinci-pdex-plan-net/ImplementationGuide/hl7.fhir.us.davinci-pdex-plan-net|1.2.0",
          "http://hl7.org/fhir/us/davinci-drug-formulary/ImplementationGuide/hl7.fhir.us.davinci-drug-formulary|2.1.0",
          "http://hl7.org/fhir/us/davinci-atr/ImplementationGuide/hl7.fhir.us.davinci-atr|2.1.0");

  @LocalServerPort private int port;

  @MockitoBean private IGenericClient payerHapiClient;

  private IGenericClient client;

  @BeforeEach
  void connectToInteropServer() {
    FhirContext fhirContext = FhirContext.forR4();
    fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
    client = fhirContext.newRestfulGenericClient("http://localhost:" + port + "/fhir");
  }

  @Test
  void metadataListsTheSupportedImplementationGuides() {
    CapabilityStatement capabilityStatement = fetchMetadata();

    assertThat(capabilityStatement.getFhirVersion()).isEqualTo(FHIRVersion._4_0_1);
    assertThat(capabilityStatement.getSoftware().getName())
        .isEqualTo("Praxis Health Plan payer-api");
    assertThat(implementationGuides(capabilityStatement)).containsExactlyElementsOf(SUPPORTED_IGS);
  }

  @Test
  void repeatedMetadataCallsDoNotDuplicateImplementationGuides() {
    fetchMetadata();

    assertThat(implementationGuides(fetchMetadata())).containsExactlyElementsOf(SUPPORTED_IGS);
  }

  @Test
  void metadataDoesNotCallThePayerHapi() {
    fetchMetadata();

    verifyNoInteractions(payerHapiClient);
  }

  private CapabilityStatement fetchMetadata() {
    return client.capabilities().ofType(CapabilityStatement.class).execute();
  }

  private static List<String> implementationGuides(CapabilityStatement capabilityStatement) {
    return capabilityStatement.getImplementationGuide().stream()
        .map(CanonicalType::getValue)
        .toList();
  }
}
