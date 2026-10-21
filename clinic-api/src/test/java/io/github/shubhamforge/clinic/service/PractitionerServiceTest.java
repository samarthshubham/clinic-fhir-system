package io.github.shubhamforge.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.shubhamforge.clinic.dto.PractitionerRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.PractitionerMapper;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import org.hl7.fhir.r4.model.Practitioner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PractitionerServiceTest {

  private FhirClientStub fhir;
  private PractitionerService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new PractitionerService(fhir.client(), new PractitionerMapper());
  }

  @Test
  void createStoresMappedPractitionerAndReturnsTheStoredCopy() {
    Practitioner practitioner =
        service.createPractitioner(
            new PractitionerRequest("Gregory", "House", "Diagnostics", null, null));

    assertThat(fhir.created()).singleElement().isNotSameAs(practitioner);
    assertThat(practitioner.getIdElement().getIdPart()).isEqualTo("1");
    assertThat(practitioner.getNameFirstRep().getFamily()).isEqualTo("House");
  }

  @Test
  void getReturnsStoredPractitioner() {
    Practitioner stored = new Practitioner();
    fhir.givenStored(stored, "dr1");

    assertThat(service.getPractitioner("dr1").getIdElement().getIdPart()).isEqualTo("dr1");
  }

  @Test
  void getUnknownPractitionerThrowsNotFound() {
    assertThatThrownBy(() -> service.getPractitioner("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Practitioner with id 'missing' not found");
  }

  @Test
  void getPropagatesOtherServerErrors() {
    fhir.givenReadFails(Practitioner.class, "x", new InternalErrorException("FHIR server error"));

    assertThatThrownBy(() -> service.getPractitioner("x"))
        .isInstanceOf(InternalErrorException.class);
  }
}
