package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.shubhamforge.clinic.dto.MedicationRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.MedicationMapper;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
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
    MedicationStatement medication =
        service.createMedication(
            new MedicationRequest("p1", "Lisinopril", "active", null, LocalDate.of(2026, 1, 10)));

    assertThat(fhir.created()).containsExactly(medication);
    assertThat(medication.getIdElement().getIdPart()).isEqualTo("1");
    assertThat(medication.getMedicationCodeableConcept().getText()).isEqualTo("Lisinopril");
  }

  @Test
  void getReturnsStoredMedication() {
    MedicationStatement stored = new MedicationStatement();
    fhir.givenStored(stored, "m1");

    assertThat(service.getMedication("m1")).isSameAs(stored);
  }

  @Test
  void getUnknownMedicationThrowsNotFound() {
    assertThatThrownBy(() -> service.getMedication("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("MedicationStatement with id 'missing' not found");
  }

  @Test
  void listsMedicationsForPatient() {
    Bundle medications = bundleOf(new MedicationStatement());
    fhir.givenSearchResult(MedicationStatement.class, medications);

    assertThat(service.getMedicationsForPatient("p1")).isSameAs(medications);
    assertThat(fhir.lastSearchCriteria(MedicationStatement.class))
        .containsExactly("subject=Patient/p1");
  }

  @Test
  void listsOnlyActiveMedications() {
    service.getActiveMedicationsForPatient("p1");

    assertThat(fhir.lastSearchCriteria(MedicationStatement.class))
        .containsExactly("subject=Patient/p1", "status=active");
  }
}
