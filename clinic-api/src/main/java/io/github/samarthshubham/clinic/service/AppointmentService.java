package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.DateClientParam;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import io.github.samarthshubham.clinic.dto.AppointmentRequest;
import io.github.samarthshubham.clinic.dto.AppointmentResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.AppointmentMapper;
import io.github.samarthshubham.clinic.mapper.PageMapper;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Service;

@Service
public class AppointmentService {

  private final IGenericClient fhirClient;
  private final AppointmentMapper appointmentMapper;

  public AppointmentService(IGenericClient fhirClient, AppointmentMapper appointmentMapper) {
    this.fhirClient = fhirClient;
    this.appointmentMapper = appointmentMapper;
  }

  public AppointmentResponse createAppointment(AppointmentRequest request) {
    var outcome = fhirClient.create().resource(appointmentMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    return appointmentMapper.toResponse(
        fhirClient.read().resource(Appointment.class).withId(id).execute());
  }

  public Appointment getAppointment(String id) {
    try {
      return fhirClient.read().resource(Appointment.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Appointment", id);
    }
  }

  public PageResponse<AppointmentResponse> getAppointmentsForPatient(String patientId) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(Appointment.class)
            .where(new ReferenceClientParam("actor").hasId("Patient/" + patientId))
            .returnBundle(Bundle.class)
            .execute();
    return PageMapper.toPage(bundle, Appointment.class, appointmentMapper::toResponse);
  }

  public Bundle getUpcomingAppointmentsForPatient(String patientId) {
    return fhirClient
        .search()
        .forResource(Appointment.class)
        .where(new ReferenceClientParam("actor").hasId("Patient/" + patientId))
        .and(new DateClientParam("date").afterOrEquals().day(LocalDate.now().toString()))
        .sort()
        .ascending("date")
        .count(1)
        .returnBundle(Bundle.class)
        .execute();
  }
}
