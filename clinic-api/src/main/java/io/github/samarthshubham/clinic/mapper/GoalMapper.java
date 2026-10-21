package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.GoalRequest;
import java.time.ZoneId;
import java.util.Date;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Goal;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
public class GoalMapper {

  private static final String LOINC_SYSTEM = "http://loinc.org";
  private static final String UCUM_SYSTEM = "http://unitsofmeasure.org";

  public Goal toFhir(GoalRequest request) {
    Goal goal = new Goal();
    goal.setSubject(new Reference("Patient/" + request.patientId()));
    goal.setLifecycleStatus(Goal.GoalLifecycleStatus.fromCode(request.status()));
    goal.setDescription(new CodeableConcept().setText(request.description()));

    if (request.targetMeasureCode() != null && request.targetValue() != null) {
      Goal.GoalTargetComponent target = new Goal.GoalTargetComponent();
      target.setMeasure(
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem(LOINC_SYSTEM)
                      .setCode(request.targetMeasureCode())
                      .setDisplay(request.targetMeasureDisplay())));
      target.setDetail(
          new Quantity()
              .setValue(request.targetValue())
              .setUnit(request.targetUnit())
              .setSystem(UCUM_SYSTEM)
              .setCode(request.targetUnit()));
      if (request.targetDate() != null) {
        Date due = Date.from(request.targetDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
        target.setDue(new DateType(due));
      }
      goal.addTarget(target);
    }
    return goal;
  }
}
