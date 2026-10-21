package io.github.samarthshubham.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;

class FhirHttpMessageConverterTest {

  private static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

  private final FhirHttpMessageConverter converter =
      new FhirHttpMessageConverter(FhirContext.forR4Cached());

  @Test
  void supportsFhirJsonAndPlainJson() {
    assertThat(converter.getSupportedMediaTypes())
        .containsExactly(FHIR_JSON, MediaType.APPLICATION_JSON);
  }

  @Test
  void writesAndReadsOnlyFhirResources() {
    assertThat(converter.canWrite(Patient.class, FHIR_JSON)).isTrue();
    assertThat(converter.canWrite(Bundle.class, MediaType.APPLICATION_JSON)).isTrue();
    assertThat(converter.canRead(Patient.class, FHIR_JSON)).isTrue();
    assertThat(converter.canWrite(String.class, MediaType.APPLICATION_JSON)).isFalse();
    assertThat(converter.canRead(Object.class, MediaType.APPLICATION_JSON)).isFalse();
    assertThat(converter.canWrite(Patient.class, MediaType.APPLICATION_XML)).isFalse();
  }

  @Test
  void writeSerialisesResourceAsFhirJson() throws IOException {
    Patient patient = new Patient().setGender(AdministrativeGender.FEMALE);
    patient.setId("p1");
    MockHttpOutputMessage output = new MockHttpOutputMessage();

    converter.write(patient, MediaType.APPLICATION_JSON, output);

    assertThat(output.getHeaders().getContentType()).isEqualTo(FHIR_JSON);
    assertThat(output.getBodyAsString(StandardCharsets.UTF_8))
        .contains("\"resourceType\": \"Patient\"")
        .contains("\"id\": \"p1\"")
        .contains("\"gender\": \"female\"");
  }

  @Test
  void writeEncodesNonAsciiTextAsUtf8() throws IOException {
    Patient patient = new Patient();
    patient.addName().setFamily("Müller");
    MockHttpOutputMessage output = new MockHttpOutputMessage();

    converter.write(patient, FHIR_JSON, output);

    assertThat(output.getBodyAsString(StandardCharsets.UTF_8)).contains("Müller");
  }

  @Test
  void readParsesFhirJsonIntoTheRequestedResourceType() throws IOException {
    MockHttpInputMessage input =
        new MockHttpInputMessage(
            """
            {"resourceType":"Patient","id":"p1","gender":"male"}
            """
                .getBytes(StandardCharsets.UTF_8));
    input.getHeaders().setContentType(FHIR_JSON);

    IBaseResource resource = converter.read(Patient.class, input);

    assertThat(resource).isInstanceOf(Patient.class);
    Patient patient = (Patient) resource;
    assertThat(patient.getIdElement().getIdPart()).isEqualTo("p1");
    assertThat(patient.getGender()).isEqualTo(AdministrativeGender.MALE);
  }
}
