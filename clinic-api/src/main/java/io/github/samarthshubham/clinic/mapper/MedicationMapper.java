package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.MedicationRequest;
import io.github.samarthshubham.clinic.dto.MedicationResponse;
import java.time.ZoneId;
import java.util.Date;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
public class MedicationMapper {

  public MedicationStatement toFhir(MedicationRequest request) {
    MedicationStatement ms = new MedicationStatement();
    ms.setSubject(new Reference("Patient/" + request.patientId()));
    ms.setMedication(new CodeableConcept().setText(request.medicationName()));
    ms.setStatus(MedicationStatement.MedicationStatementStatus.fromCode(request.status()));
    if (request.dosageText() != null) {
      ms.addDosage(new Dosage().setText(request.dosageText()));
    }
    Date start = Date.from(request.startDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
    ms.setEffective(new Period().setStart(start));
    return ms;
  }

  public MedicationResponse toResponse(MedicationStatement statement) {
    return new MedicationResponse(
        FhirValues.idOf(statement),
        FhirValues.referenceId(statement.getSubject()),
        statement.hasMedicationCodeableConcept()
            ? statement.getMedicationCodeableConcept().getText()
            : null,
        statement.hasStatus() ? statement.getStatus().toCode() : null,
        statement.hasDosage() ? statement.getDosageFirstRep().getText() : null,
        statement.hasEffectivePeriod()
            ? FhirValues.localDate(statement.getEffectivePeriod().getStartElement())
            : null);
  }
}
