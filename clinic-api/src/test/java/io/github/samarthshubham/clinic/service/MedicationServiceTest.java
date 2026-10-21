package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.dto.MedicationRequest;
import io.github.samarthshubham.clinic.dto.MedicationResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.MedicationMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MedicationServiceTest {

  private FhirClientStub fhir;
  private MedicationService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new MedicationService(fhir.client(), new MedicationMapper());
  }

  @Test
  void createStoresMappedMedicationAndReturnsTheStoredCopy() {
    MedicationResponse medication =
        service.createMedication(
            new MedicationRequest("p1", "Lisinopril", "active", null, LocalDate.of(2026, 1, 10)));

    assertThat(fhir.created()).singleElement().isInstanceOf(MedicationStatement.class);
    assertThat(medication)
        .isEqualTo(
            new MedicationResponse(
                "1", "p1", "Lisinopril", "active", null, LocalDate.of(2026, 1, 10)));
  }

  @Test
  void getReturnsStoredMedication() {
    MedicationStatement stored = new MedicationStatement();
    fhir.givenStored(stored, "m1");

    assertThat(service.getMedication("m1").getIdElement().getIdPart()).isEqualTo("m1");
  }

  @Test
  void getUnknownMedicationThrowsNotFound() {
    assertThatThrownBy(() -> service.getMedication("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("MedicationStatement with id 'missing' not found");
  }

  @Test
  void getPropagatesOtherServerErrors() {
    fhir.givenReadFails(
        MedicationStatement.class, "x", new InternalErrorException("FHIR server error"));

    assertThatThrownBy(() -> service.getMedication("x")).isInstanceOf(InternalErrorException.class);
  }

  @Test
  void listsMedicationsForPatient() {
    MedicationStatement stored = new MedicationStatement();
    stored.setId("m1");
    fhir.givenSearchResult(MedicationStatement.class, bundleOf(stored));

    assertThat(service.getMedicationsForPatient("p1").items())
        .extracting(MedicationResponse::id)
        .containsExactly("m1");
    assertThat(fhir.lastSearchCriteria(MedicationStatement.class))
        .containsExactlyInAnyOrder("subject=Patient/p1");
  }

  @Test
  void listsOnlyActiveMedications() {
    service.getActiveMedicationsForPatient("p1");

    assertThat(fhir.lastSearchCriteria(MedicationStatement.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", "status=active");
  }
}
