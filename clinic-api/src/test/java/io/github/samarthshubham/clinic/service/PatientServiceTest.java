package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.dto.PatientRequest;
import io.github.samarthshubham.clinic.dto.PatientResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.PatientMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
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
    PatientResponse patient =
        service.createPatient(
            new PatientRequest("Priya", "Nair", LocalDate.of(1985, 3, 14), "female", null, null));

    assertThat(fhir.created()).singleElement().isInstanceOf(Patient.class);
    assertThat(patient)
        .isEqualTo(
            new PatientResponse("1", "Priya", "Nair", "1985-03-14", "female", null, null, null));
  }

  @Test
  void getReturnsStoredPatientAsResponse() {
    fhir.givenStored(new Patient().setActive(true), "p1");

    assertThat(service.getPatient("p1"))
        .isEqualTo(new PatientResponse("p1", null, null, null, null, null, null, true));
  }

  @Test
  void findReturnsStoredFhirPatient() {
    fhir.givenStored(new Patient(), "p1");

    assertThat(service.findPatient("p1").getIdElement().getIdPart()).isEqualTo("p1");
  }

  @Test
  void getUnknownPatientThrowsNotFound() {
    assertThatThrownBy(() -> service.getPatient("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Patient with id 'missing' not found");
  }

  @Test
  void getPropagatesOtherServerErrors() {
    fhir.givenReadFails(Patient.class, "x", new InternalErrorException("FHIR server error"));

    assertThatThrownBy(() -> service.getPatient("x")).isInstanceOf(InternalErrorException.class);
  }

  @Test
  void searchWithoutFiltersListsAllPatients() {
    Patient first = new Patient().setActive(true);
    first.setId("p1");
    Patient second = new Patient().setActive(false);
    second.setId("p2");
    fhir.givenSearchResult(Patient.class, bundleOf(first, second));

    PageResponse<PatientResponse> page = service.searchPatients(null, null);

    assertThat(page.items()).extracting(PatientResponse::id).containsExactly("p1", "p2");
    assertThat(page.total()).isEqualTo(2);
    assertThat(fhir.lastSearchCriteria(Patient.class)).isEmpty();
  }

  @Test
  void searchFiltersByNameAndBirthDate() {
    service.searchPatients("Nair", LocalDate.of(1985, 3, 14));

    assertThat(fhir.lastSearchCriteria(Patient.class))
        .containsExactlyInAnyOrder("name=Nair", "birthdate=1985-03-14");
  }

  @Test
  void searchFiltersByNameOnly() {
    service.searchPatients("Nair", null);

    assertThat(fhir.lastSearchCriteria(Patient.class)).containsExactly("name=Nair");
  }

  @Test
  void searchFiltersByBirthDateOnly() {
    service.searchPatients(null, LocalDate.of(1985, 3, 14));

    assertThat(fhir.lastSearchCriteria(Patient.class)).containsExactly("birthdate=1985-03-14");
  }

  @Test
  void blankNameIsIgnored() {
    service.searchPatients("  ", null);

    assertThat(fhir.lastSearchCriteria(Patient.class)).isEmpty();
  }
}
