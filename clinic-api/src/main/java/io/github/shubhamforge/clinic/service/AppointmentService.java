package io.github.shubhamforge.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.DateClientParam;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import io.github.shubhamforge.clinic.dto.AppointmentRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.AppointmentMapper;
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

  public Appointment createAppointment(AppointmentRequest request) {
    var outcome = fhirClient.create().resource(appointmentMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    return fhirClient.read().resource(Appointment.class).withId(id).execute();
  }

  public Appointment getAppointment(String id) {
    try {
      return fhirClient.read().resource(Appointment.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Appointment", id);
    }
  }

  public Bundle getAppointmentsForPatient(String patientId) {
    return fhirClient
        .search()
        .forResource(Appointment.class)
        .where(new ReferenceClientParam("actor").hasId("Patient/" + patientId))
        .returnBundle(Bundle.class)
        .execute();
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
