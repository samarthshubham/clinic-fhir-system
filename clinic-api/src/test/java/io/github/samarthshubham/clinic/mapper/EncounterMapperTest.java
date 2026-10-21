package io.github.samarthshubham.clinic.mapper;

import static io.github.samarthshubham.clinic.support.FhirDates.localDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.clinic.dto.EncounterRequest;
import io.github.samarthshubham.clinic.dto.EncounterResponse;
import java.time.LocalDate;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

class EncounterMapperTest {

  private static final LocalDate VISIT = LocalDate.of(2026, 5, 1);

  private final EncounterMapper mapper = new EncounterMapper();

  @Test
  void mapsAllFields() {
    Encounter encounter =
        mapper.toFhir(
            new EncounterRequest("p1", VISIT, "Headache", "finished", "dr1", "Rest advised"));

    assertThat(encounter.getSubject().getReference()).isEqualTo("Patient/p1");
    assertThat(encounter.getStatus()).isEqualTo(Encounter.EncounterStatus.FINISHED);
    assertThat(localDate(encounter.getPeriod().getStart())).isEqualTo(VISIT);
    assertThat(encounter.getReasonCodeFirstRep().getText()).isEqualTo("Headache");
    assertThat(
            ((StringType)
                    encounter.getExtensionByUrl(EncounterMapper.NOTE_EXTENSION_URL).getValue())
                .getValue())
        .isEqualTo("Rest advised");
    Encounter.EncounterParticipantComponent participant = encounter.getParticipantFirstRep();
    assertThat(participant.getIndividual().getReference()).isEqualTo("Practitioner/dr1");
    Coding type = participant.getTypeFirstRep().getCodingFirstRep();
    assertThat(type.getSystem())
        .isEqualTo("http://terminology.hl7.org/CodeSystem/v3-ParticipationType");
    assertThat(type.getCode()).isEqualTo("ATND");
    assertThat(type.getDisplay()).isEqualTo("Attender");
  }

  @Test
  void omitsOptionalFieldsWhenNotGiven() {
    Encounter encounter =
        mapper.toFhir(new EncounterRequest("p1", VISIT, null, "planned", null, null));

    assertThat(encounter.getReasonCode()).isEmpty();
    assertThat(encounter.getExtension()).isEmpty();
    assertThat(encounter.getParticipant()).isEmpty();
  }

  @Test
  void unknownStatusIsRejected() {
    EncounterRequest request = new EncounterRequest("p1", VISIT, null, "done", null, null);

    assertThatThrownBy(() -> mapper.toFhir(request)).isInstanceOf(FHIRException.class);
  }

  @Test
  void toResponseIsTheReverseOfToFhir() {
    Encounter encounter =
        mapper.toFhir(
            new EncounterRequest("p1", VISIT, "Headache", "finished", "dr1", "Rest advised"));
    encounter.setId("e1");

    assertThat(mapper.toResponse(encounter))
        .isEqualTo(
            new EncounterResponse(
                "e1", "p1", VISIT, "Headache", "finished", "dr1", "Rest advised"));
  }

  @Test
  void toResponseLeavesMissingFieldsNull() {
    Encounter encounter = new Encounter();
    encounter.setId("e1");

    assertThat(mapper.toResponse(encounter))
        .isEqualTo(new EncounterResponse("e1", null, null, null, null, null, null));
  }

  @Test
  void attendingPractitionerIgnoresOtherParticipants() {
    Encounter encounter = new Encounter();
    encounter.addParticipant().setIndividual(new Reference("Practitioner/other"));
    encounter
        .addParticipant()
        .setIndividual(new Reference("RelatedPerson/r1"))
        .addType()
        .addCoding()
        .setCode("ATND");
    encounter
        .addParticipant()
        .setIndividual(new Reference("Practitioner/dr1"))
        .addType()
        .addCoding()
        .setCode("ATND");

    assertThat(mapper.attendingPractitionerId(encounter)).isEqualTo("dr1");
  }

  @Test
  void toResponseKeepsTheStoredVisitDateNearMidnight() {
    Encounter encounter = new Encounter();
    encounter.getPeriod().setStartElement(new DateTimeType("2026-05-01T23:30:00-05:00"));
    encounter.setId("e1");

    assertThat(mapper.toResponse(encounter).visitDate()).isEqualTo(LocalDate.of(2026, 5, 1));
  }

  @Test
  void attendingPractitionerAcceptsAbsoluteReferences() {
    Encounter encounter = new Encounter();
    encounter
        .addParticipant()
        .setIndividual(new Reference("http://hapi.example/fhir/Practitioner/dr1"))
        .addType()
        .addCoding()
        .setCode("ATND");

    assertThat(mapper.attendingPractitionerId(encounter)).isEqualTo("dr1");
  }

  @Test
  void noteIsNullWhenTheExtensionHasNoValue() {
    Encounter encounter = new Encounter();
    encounter.addExtension().setUrl(EncounterMapper.NOTE_EXTENSION_URL);

    assertThat(mapper.note(encounter)).isNull();
  }
}
