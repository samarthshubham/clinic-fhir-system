package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.shubhamforge.clinic.dto.AppointmentRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.AppointmentMapper;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    assertThat(fhir.created()).containsExactly(appointment);
    assertThat(appointment.getIdElement().getIdPart()).isEqualTo("1");
    assertThat(appointment.getParticipantFirstRep().getActor().getReference())
        .isEqualTo("Patient/p1");
  }

  @Test
  void getReturnsStoredAppointment() {
    Appointment stored = new Appointment();
    fhir.givenStored(stored, "a1");

    assertThat(service.getAppointment("a1")).isSameAs(stored);
  }

  @Test
  void getUnknownAppointmentThrowsNotFound() {
    assertThatThrownBy(() -> service.getAppointment("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Appointment with id 'missing' not found");
  }

  @Test
  void listsAppointmentsWherePatientIsAnActor() {
    Bundle appointments = bundleOf(new Appointment());
    fhir.givenSearchResult(Appointment.class, appointments);

    assertThat(service.getAppointmentsForPatient("p1")).isSameAs(appointments);
    assertThat(fhir.lastSearchCriteria(Appointment.class)).containsExactly("actor=Patient/p1");
  }

  @Test
  void upcomingAppointmentIsTheNextOneFromToday() {
    LocalDate before = LocalDate.now();

    service.getUpcomingAppointmentsForPatient("p1");

    assertThat(fhir.lastSearchCriteria(Appointment.class))
        .containsAnyOf("date=ge" + before, "date=ge" + LocalDate.now())
        .contains("actor=Patient/p1", "_count=1")
        .hasSize(3);
  }
}
