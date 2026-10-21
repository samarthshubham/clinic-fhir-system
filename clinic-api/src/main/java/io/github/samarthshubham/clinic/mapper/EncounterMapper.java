package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.EncounterRequest;
import io.github.samarthshubham.clinic.dto.EncounterResponse;
import java.time.ZoneId;
import java.util.Date;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

@Component
public class EncounterMapper {

  private static final String PARTICIPATION_TYPE_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/v3-ParticipationType";
  public static final String NOTE_EXTENSION_URL =
      "http://praxis.clinic/fhir/extension/encounter-note";

  public Encounter toFhir(EncounterRequest request) {
    Encounter encounter = new Encounter();
    encounter.setSubject(new Reference("Patient/" + request.patientId()));
    encounter.setStatus(Encounter.EncounterStatus.fromCode(request.status()));
    Date startDate =
        Date.from(request.visitDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
    encounter.setPeriod(new Period().setStart(startDate));
    if (request.reason() != null) {
      encounter.addReasonCode(new CodeableConcept().setText(request.reason()));
    }
    if (request.note() != null) {
      encounter.addExtension(NOTE_EXTENSION_URL, new StringType(request.note()));
    }
    if (request.practitionerId() != null) {
      Encounter.EncounterParticipantComponent participant =
          new Encounter.EncounterParticipantComponent();
      participant.addType(
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem(PARTICIPATION_TYPE_SYSTEM)
                      .setCode("ATND")
                      .setDisplay("Attender")));
      participant.setIndividual(new Reference("Practitioner/" + request.practitionerId()));
      encounter.addParticipant(participant);
    }
    return encounter;
  }

  public EncounterResponse toResponse(Encounter encounter) {
    return new EncounterResponse(
        FhirValues.idOf(encounter),
        FhirValues.referenceId(encounter.getSubject()),
        FhirValues.localDate(encounter.getPeriod().getStartElement()),
        encounter.hasReasonCode() ? encounter.getReasonCodeFirstRep().getText() : null,
        encounter.hasStatus() ? encounter.getStatus().toCode() : null,
        attendingPractitionerId(encounter),
        note(encounter));
  }

  public String attendingPractitionerId(Encounter encounter) {
    return encounter.getParticipant().stream()
        .filter(EncounterMapper::isAttender)
        .map(participant -> participant.getIndividual().getReference())
        .filter(ref -> ref != null && ref.startsWith("Practitioner/"))
        .map(ref -> ref.substring("Practitioner/".length()))
        .findFirst()
        .orElse(null);
  }

  public String note(Encounter encounter) {
    var extension = encounter.getExtensionByUrl(NOTE_EXTENSION_URL);
    return extension != null ? extension.getValue().primitiveValue() : null;
  }

  private static boolean isAttender(Encounter.EncounterParticipantComponent participant) {
    return participant.getType().stream()
        .flatMap(type -> type.getCoding().stream())
        .anyMatch(coding -> "ATND".equals(coding.getCode()));
  }
}
