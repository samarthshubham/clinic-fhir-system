package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.shubhamforge.clinic.dto.EncounterRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.EncounterMapper;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
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
    Encounter encounter =
        service.createEncounter(
            new EncounterRequest(
                "p1", LocalDate.of(2026, 5, 1), "Headache", "finished", null, null));

    assertThat(fhir.created()).containsExactly(encounter);
    assertThat(encounter.getIdElement().getIdPart()).isEqualTo("1");
    assertThat(encounter.getSubject().getReference()).isEqualTo("Patient/p1");
  }

  @Test
  void getReturnsStoredEncounter() {
    Encounter stored = new Encounter();
    fhir.givenStored(stored, "e1");

    assertThat(service.getEncounter("e1")).isSameAs(stored);
  }

  @Test
  void getUnknownEncounterThrowsNotFound() {
    assertThatThrownBy(() -> service.getEncounter("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Encounter with id 'missing' not found");
  }

  @Test
  void listsEncountersForPatient() {
    Bundle encounters = bundleOf(new Encounter());
    fhir.givenSearchResult(Encounter.class, encounters);

    assertThat(service.getEncountersForPatient("p1")).isSameAs(encounters);
    assertThat(fhir.lastSearchCriteria(Encounter.class)).containsExactly("subject=Patient/p1");
  }
}
