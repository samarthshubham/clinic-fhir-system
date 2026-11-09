package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.ConditionRequest;
import io.github.samarthshubham.clinic.dto.ConditionResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
public class ConditionMapper {

  private static final String CLINICAL_STATUS_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/condition-clinical";
  private static final String CATEGORY_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/condition-category";

  public Condition toFhir(ConditionRequest request) {
    Condition condition = new Condition();
    condition.getMeta().addProfile(UsCoreProfiles.CONDITION_PROBLEMS);
    condition.addCategory(
        new CodeableConcept()
            .addCoding(
                new Coding()
                    .setSystem(CATEGORY_SYSTEM)
                    .setCode("problem-list-item")
                    .setDisplay("Problem List Item")));
    condition.setSubject(new Reference("Patient/" + request.patientId()));
    if (request.encounterId() != null) {
      condition.setEncounter(new Reference("Encounter/" + request.encounterId()));
    }
    condition.setCode(
        new CodeableConcept()
            .addCoding(new Coding().setCode(request.code()))
            .setText(request.display()));
    condition.setClinicalStatus(
        new CodeableConcept()
            .addCoding(
                new Coding().setSystem(CLINICAL_STATUS_SYSTEM).setCode(request.clinicalStatus())));
    condition.setOnset(new DateTimeType(request.onsetDate().toString()));
    return condition;
  }

  public ConditionResponse toResponse(Condition condition) {
    return new ConditionResponse(
        FhirValues.idOf(condition),
        FhirValues.referenceId(condition.getSubject()),
        FhirValues.referenceId(condition.getEncounter()),
        firstCode(condition.getCode()),
        condition.getCode().getText(),
        firstCode(condition.getClinicalStatus()),
        condition.hasOnsetDateTimeType()
            ? FhirValues.localDate(condition.getOnsetDateTimeType())
            : null);
  }

  private static String firstCode(CodeableConcept concept) {
    return concept.hasCoding() ? concept.getCodingFirstRep().getCode() : null;
  }
}
