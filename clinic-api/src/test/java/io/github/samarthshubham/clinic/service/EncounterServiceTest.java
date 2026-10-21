package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.dto.EncounterRequest;
import io.github.samarthshubham.clinic.dto.EncounterResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.EncounterMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Encounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncounterServiceTest {

  private FhirClientStub fhir;
  private EncounterService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new EncounterService(fhir.client(), new EncounterMapper());
  }

  @Test
  void createStoresMappedEncounterAndReturnsTheStoredCopy() {
    EncounterResponse encounter =
        service.createEncounter(
            new EncounterRequest(
                "p1", LocalDate.of(2026, 5, 1), "Headache", "finished", null, null));

    assertThat(fhir.created()).singleElement().isInstanceOf(Encounter.class);
    assertThat(encounter)
        .isEqualTo(
            new EncounterResponse(
                "1", "p1", LocalDate.of(2026, 5, 1), "Headache", "finished", null, null));
  }

  @Test
  void getReturnsStoredEncounter() {
    Encounter stored = new Encounter();
    fhir.givenStored(stored, "e1");

    assertThat(service.getEncounter("e1").id()).isEqualTo("e1");
  }

  @Test
  void getUnknownEncounterThrowsNotFound() {
    assertThatThrownBy(() -> service.getEncounter("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Encounter with id 'missing' not found");
  }

  @Test
  void getPropagatesOtherServerErrors() {
    fhir.givenReadFails(Encounter.class, "x", new InternalErrorException("FHIR server error"));

    assertThatThrownBy(() -> service.getEncounter("x")).isInstanceOf(InternalErrorException.class);
  }

  @Test
  void listsEncountersForPatient() {
    Encounter stored = new Encounter();
    stored.setId("e1");
    fhir.givenSearchResult(Encounter.class, bundleOf(stored));

    assertThat(service.getEncountersForPatient("p1").items())
        .extracting(EncounterResponse::id)
        .containsExactly("e1");
    assertThat(fhir.lastSearchCriteria(Encounter.class))
        .containsExactlyInAnyOrder("subject=Patient/p1");
  }
}
