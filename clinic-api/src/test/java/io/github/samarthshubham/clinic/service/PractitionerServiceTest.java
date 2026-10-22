package io.github.samarthshubham.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.dto.PractitionerRequest;
import io.github.samarthshubham.clinic.dto.PractitionerResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.PractitionerMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import org.hl7.fhir.r4.model.Bundle;
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
    PractitionerResponse practitioner =
        service.createPractitioner(
            new PractitionerRequest("Gregory", "House", "Diagnostics", null, null));

    assertThat(fhir.created()).singleElement().isInstanceOf(Practitioner.class);
    assertThat(practitioner)
        .isEqualTo(new PractitionerResponse("1", "Gregory", "House", "Diagnostics", null, null));
  }

  @Test
  void getReturnsStoredPractitionerAsResponse() {
    Practitioner stored = new Practitioner();
    stored.addName().setFamily("House");
    fhir.givenStored(stored, "dr1");

    assertThat(service.getPractitioner("dr1"))
        .isEqualTo(new PractitionerResponse("dr1", null, "House", null, null, null));
  }

  @Test
  void findReturnsStoredFhirPractitioner() {
    fhir.givenStored(new Practitioner(), "dr1");

    assertThat(service.findPractitioner("dr1").getIdElement().getIdPart()).isEqualTo("dr1");
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

  @Test
  void upsertStaffPractitionerUpdatesUnderDeterministicId() {
    service.upsertStaffPractitioner(
        "frontdesk", new PractitionerRequest("Jordan", "Lee", "Front desk", null, null));

    assertThat(fhir.updated())
        .singleElement()
        .satisfies(
            resource ->
                assertThat(resource.getIdElement().getIdPart()).isEqualTo("user-frontdesk"));
  }

  @Test
  void findByUsernameSearchesTheUsernameIdentifier() {
    Practitioner stored = new Practitioner();
    stored.setId("user-clinician");
    stored.addName().setFamily("Patel").addGiven("Anika");
    Bundle bundle = new Bundle();
    bundle.addEntry().setResource(stored);
    fhir.givenSearchResultWhen(
        Practitioner.class,
        bundle,
        "identifier=" + PractitionerMapper.USERNAME_SYSTEM + "|clinician");

    assertThat(service.findByUsername("clinician"))
        .hasValue(new PractitionerResponse("user-clinician", "Anika", "Patel", null, null, null));
  }

  @Test
  void findByUsernameIsEmptyWhenNoPractitionerMatches() {
    fhir.givenSearchResult(Practitioner.class, new Bundle());

    assertThat(service.findByUsername("ghost")).isEmpty();
  }
}
