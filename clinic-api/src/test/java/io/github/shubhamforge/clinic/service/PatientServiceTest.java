package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.shubhamforge.clinic.dto.PatientRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.PatientMapper;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PatientServiceTest {

  private FhirClientStub fhir;
  private PatientService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new PatientService(fhir.client(), new PatientMapper());
  }

  @Test
  void createStoresMappedPatientAndReturnsTheStoredCopy() {
    Patient patient =
        service.createPatient(
            new PatientRequest("Priya", "Nair", LocalDate.of(1985, 3, 14), "female", null, null));

    assertThat(fhir.created()).containsExactly(patient);
    assertThat(patient.getIdElement().getIdPart()).isEqualTo("1");
    assertThat(patient.getNameFirstRep().getFamily()).isEqualTo("Nair");
  }

  @Test
  void getReturnsStoredPatient() {
    Patient stored = new Patient();
    fhir.givenStored(stored, "p1");

    assertThat(service.getPatient("p1")).isSameAs(stored);
  }

  @Test
  void getUnknownPatientThrowsNotFound() {
    assertThatThrownBy(() -> service.getPatient("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Patient with id 'missing' not found");
  }

  @Test
  void searchWithoutFiltersListsAllPatients() {
    Bundle patients = bundleOf(new Patient(), new Patient());
    fhir.givenSearchResult(Patient.class, patients);

    assertThat(service.searchPatients(null, null)).isSameAs(patients);
    assertThat(fhir.lastSearchCriteria(Patient.class)).isEmpty();
  }

  @Test
  void searchFiltersByNameAndBirthDate() {
    service.searchPatients("Nair", LocalDate.of(1985, 3, 14));

    assertThat(fhir.lastSearchCriteria(Patient.class))
        .containsExactly("name=Nair", "birthdate=1985-03-14");
  }

  @Test
  void blankNameIsIgnored() {
    service.searchPatients("  ", null);

    assertThat(fhir.lastSearchCriteria(Patient.class)).isEmpty();
  }
}
