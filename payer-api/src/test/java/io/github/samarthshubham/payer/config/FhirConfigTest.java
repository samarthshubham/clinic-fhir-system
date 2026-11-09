package io.github.samarthshubham.payer.config;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FhirConfigTest {

  @Autowired private FhirContext fhirContext;

  @Autowired private IGenericClient fhirClient;

  @Test
  void usesFhirR4() {
    assertThat(fhirContext.getVersion().getVersion()).isEqualTo(FhirVersionEnum.R4);
  }

  @Test
  void clientPointsAtThePayerHapi() {
    assertThat(fhirClient.getServerBase()).isEqualTo("http://localhost:8081/fhir");
  }
}
