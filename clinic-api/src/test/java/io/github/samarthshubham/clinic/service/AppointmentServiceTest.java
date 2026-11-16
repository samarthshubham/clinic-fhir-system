package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.client.CrdClient;
import io.github.samarthshubham.clinic.client.CrdResult;
import io.github.samarthshubham.clinic.dto.AppointmentRequest;
import io.github.samarthshubham.clinic.dto.AppointmentResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.AppointmentMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Extension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppointmentServiceTest {

  private FhirClientStub fhir;
  private AppointmentService service;
  private CrdService crd;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    crd = mock(CrdService.class);
    when(crd.checkAppointment(any(), any(), any())).thenReturn(CrdResult.none());
    service = new AppointmentService(fhir.client(), new AppointmentMapper(), crd);
  }

  @Test
  void createCallsAppointmentBookAndStoresTheCoverageInformation() {
    Extension coverageInformation = new Extension(CrdClient.COVERAGE_INFORMATION);
    when(crd.checkAppointment(any(Appointment.class), eq("p1"), eq("dr1")))
        .thenReturn(new CrdResult(List.of(coverageInformation), List.of()));
    LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 30);

    service.createAppointment(
        new AppointmentRequest("p1", "dr1", start, start.plusMinutes(30), "MRI", "booked"));

    Appointment stored = fhir.stored(Appointment.class, "1");
    assertThat(stored.getExtensionsByUrl(CrdClient.COVERAGE_INFORMATION)).hasSize(1);
  }

  @Test
  void createStoresMappedAppointmentAndReturnsTheStoredCopy() {
    LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 30);
    AppointmentResponse appointment =
        service.createAppointment(
            new AppointmentRequest("p1", null, start, start.plusMinutes(30), null, "booked"));

    assertThat(fhir.created()).singleElement().isInstanceOf(Appointment.class);
    assertThat(appointment.id()).isEqualTo("1");
    assertThat(appointment.patientId()).isEqualTo("p1");
    assertThat(appointment.start().toLocalDateTime()).isEqualTo(start);
    assertThat(appointment.status()).isEqualTo("booked");
  }

  @Test
  void getReturnsStoredAppointment() {
    Appointment stored = new Appointment();
    fhir.givenStored(stored, "a1");

    assertThat(service.getAppointment("a1").getIdElement().getIdPart()).isEqualTo("a1");
  }

  @Test
  void getUnknownAppointmentThrowsNotFound() {
    assertThatThrownBy(() -> service.getAppointment("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Appointment with id 'missing' not found");
  }

  @Test
  void getPropagatesOtherServerErrors() {
    fhir.givenReadFails(Appointment.class, "x", new InternalErrorException("FHIR server error"));

    assertThatThrownBy(() -> service.getAppointment("x"))
        .isInstanceOf(InternalErrorException.class);
  }

  @Test
  void listsAppointmentsWherePatientIsAnActor() {
    Appointment stored = new Appointment();
    stored.setId("a1");
    fhir.givenSearchResult(Appointment.class, bundleOf(stored));

    assertThat(service.getAppointmentsForPatient("p1").items())
        .extracting(AppointmentResponse::id)
        .containsExactly("a1");
    assertThat(fhir.lastSearchCriteria(Appointment.class))
        .containsExactlyInAnyOrder("actor=Patient/p1");
  }

  @Test
  void upcomingAppointmentIsTheSoonestOneFromToday() {
    LocalDate before = LocalDate.now();

    service.getUpcomingAppointmentsForPatient("p1");

    List<String> criteria = fhir.lastSearchCriteria(Appointment.class);
    assertThat(criteria)
        .filteredOn(c -> !c.startsWith("date="))
        .containsExactlyInAnyOrder("actor=Patient/p1", "_sort=date", "_count=1");
    assertThat(criteria)
        .filteredOn(c -> c.startsWith("date="))
        .singleElement()
        .satisfies(
            c ->
                assertThat(LocalDate.parse(c.substring("date=ge".length())))
                    .isBetween(before, LocalDate.now()));
  }
}
