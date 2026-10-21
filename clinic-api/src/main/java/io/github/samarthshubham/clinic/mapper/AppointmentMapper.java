package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.AppointmentRequest;
import io.github.samarthshubham.clinic.dto.AppointmentResponse;
import java.time.ZoneId;
import java.util.Date;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
public class AppointmentMapper {

  public Appointment toFhir(AppointmentRequest request) {
    Appointment appointment = new Appointment();
    appointment.setStatus(Appointment.AppointmentStatus.fromCode(request.status()));
    if (request.description() != null) {
      appointment.setDescription(request.description());
    }
    Date start = Date.from(request.start().atZone(ZoneId.systemDefault()).toInstant());
    Date end = Date.from(request.end().atZone(ZoneId.systemDefault()).toInstant());
    appointment.setStart(start);
    appointment.setEnd(end);

    Appointment.AppointmentParticipantComponent patientParticipant =
        new Appointment.AppointmentParticipantComponent();
    patientParticipant.setActor(new Reference("Patient/" + request.patientId()));
    patientParticipant.setStatus(Appointment.ParticipationStatus.ACCEPTED);
    appointment.addParticipant(patientParticipant);

    if (request.practitionerId() != null) {
      Appointment.AppointmentParticipantComponent providerParticipant =
          new Appointment.AppointmentParticipantComponent();
      providerParticipant.setActor(new Reference("Practitioner/" + request.practitionerId()));
      providerParticipant.setStatus(Appointment.ParticipationStatus.ACCEPTED);
      appointment.addParticipant(providerParticipant);
    }
    return appointment;
  }

  public AppointmentResponse toResponse(Appointment appointment) {
    return new AppointmentResponse(
        FhirValues.idOf(appointment),
        participantId(appointment, "Patient"),
        participantId(appointment, "Practitioner"),
        FhirValues.offsetDateTime(appointment.getStart()),
        FhirValues.offsetDateTime(appointment.getEnd()),
        appointment.getDescription(),
        appointment.hasStatus() ? appointment.getStatus().toCode() : null);
  }

  private static String participantId(Appointment appointment, String resourceType) {
    return appointment.getParticipant().stream()
        .map(participant -> participant.getActor().getReferenceElement())
        .filter(ref -> resourceType.equals(ref.getResourceType()))
        .map(ref -> ref.getIdPart())
        .findFirst()
        .orElse(null);
  }
}
