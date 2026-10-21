package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.dto.AppointmentRequest;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.AppointmentMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppointmentServiceTest {

  private FhirClientStub fhir;
  private AppointmentService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new AppointmentService(fhir.client(), new AppointmentMapper());
  }

  @Test
  void createStoresMappedAppointmentAndReturnsTheStoredCopy() {
    LocalDateTime start = LocalDateTime.of(2026, 6, 1, 9, 30);
    Appointment appointment =
        service.createAppointment(
            new AppointmentRequest("p1", null, start, start.plusMinutes(30), null, "booked"));

    assertThat(fhir.created()).singleElement().isNotSameAs(appointment);
    assertThat(appointment.getIdElement().getIdPart()).isEqualTo("1");
    assertThat(appointment.getParticipantFirstRep().getActor().getReference())
        .isEqualTo("Patient/p1");
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
    Bundle appointments = bundleOf(new Appointment());
    fhir.givenSearchResult(Appointment.class, appointments);

    assertThat(service.getAppointmentsForPatient("p1")).isSameAs(appointments);
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
