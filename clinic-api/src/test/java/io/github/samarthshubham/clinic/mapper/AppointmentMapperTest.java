package io.github.samarthshubham.clinic.mapper;

import static io.github.samarthshubham.clinic.support.FhirDates.localDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.samarthshubham.clinic.dto.AppointmentRequest;
import io.github.samarthshubham.clinic.dto.AppointmentResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

class AppointmentMapperTest {

  private static final LocalDateTime START = LocalDateTime.of(2026, 6, 1, 9, 30);
  private static final LocalDateTime END = LocalDateTime.of(2026, 6, 1, 10, 0);

  private final AppointmentMapper mapper = new AppointmentMapper();

  @Test
  void mapsTimesStatusDescriptionAndBothParticipants() {
    Appointment appointment =
        mapper.toFhir(new AppointmentRequest("p1", "dr1", START, END, "Follow-up", "booked"));

    assertThat(appointment.getStatus()).isEqualTo(Appointment.AppointmentStatus.BOOKED);
    assertThat(appointment.getDescription()).isEqualTo("Follow-up");
    assertThat(localDateTime(appointment.getStart())).isEqualTo(START);
    assertThat(localDateTime(appointment.getEnd())).isEqualTo(END);
    assertThat(appointment.getParticipant())
        .extracting(p -> p.getActor().getReference(), p -> p.getStatus())
        .containsExactly(
            tuple("Patient/p1", Appointment.ParticipationStatus.ACCEPTED),
            tuple("Practitioner/dr1", Appointment.ParticipationStatus.ACCEPTED));
  }

  @Test
  void unknownStatusIsRejected() {
    AppointmentRequest request = new AppointmentRequest("p1", null, START, END, null, "confirmed");

    assertThatThrownBy(() -> mapper.toFhir(request)).isInstanceOf(FHIRException.class);
  }

  @Test
  void onlyPatientParticipatesWhenNoPractitionerGiven() {
    Appointment appointment =
        mapper.toFhir(new AppointmentRequest("p1", null, START, END, null, "proposed"));

    assertThat(appointment.hasDescription()).isFalse();
    assertThat(appointment.getParticipant())
        .extracting(p -> p.getActor().getReference())
        .containsExactly("Patient/p1");
  }

  @Test
  void toResponseIsTheReverseOfToFhir() {
    Appointment appointment =
        mapper.toFhir(new AppointmentRequest("p1", "dr1", START, END, "Follow-up", "booked"));
    appointment.setId("a1");

    assertThat(mapper.toResponse(appointment))
        .isEqualTo(
            new AppointmentResponse(
                "a1",
                "p1",
                "dr1",
                START.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                END.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                "Follow-up",
                "booked"));
  }

  @Test
  void toResponseFindsParticipantsByResourceType() {
    Appointment appointment = new Appointment();
    appointment.addParticipant().setActor(new Reference("Location/l1"));
    appointment.addParticipant().setActor(new Reference("Practitioner/dr1"));
    appointment.addParticipant().setActor(new Reference("Patient/p1"));
    appointment.setId("a1");

    assertThat(mapper.toResponse(appointment))
        .isEqualTo(new AppointmentResponse("a1", "p1", "dr1", null, null, null, null));
  }

  @Test
  void toResponseKeepsTheStoredOffset() {
    Appointment appointment = new Appointment();
    appointment.setStartElement(new InstantType("2026-11-20T09:00:00-05:00"));
    appointment.setEndElement(new InstantType("2026-11-20T09:30:00+05:30"));
    appointment.setId("a1");

    AppointmentResponse response = mapper.toResponse(appointment);

    assertThat(response.start()).isEqualTo(OffsetDateTime.parse("2026-11-20T09:00:00-05:00"));
    assertThat(response.end()).isEqualTo(OffsetDateTime.parse("2026-11-20T09:30:00+05:30"));
  }
}
