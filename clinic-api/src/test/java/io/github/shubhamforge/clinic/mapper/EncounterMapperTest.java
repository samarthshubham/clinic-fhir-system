package io.github.shubhamforge.clinic.mapper;

import static io.github.shubhamforge.clinic.support.FhirDates.localDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.shubhamforge.clinic.dto.EncounterRequest;
import java.time.LocalDate;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
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
}
