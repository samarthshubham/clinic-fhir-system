package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.CarePlanRequest;
import io.github.samarthshubham.clinic.dto.CarePlanResponse;
import java.time.ZoneId;
import java.util.Date;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
public class CarePlanMapper {

  public CarePlan toFhir(CarePlanRequest request) {
    CarePlan plan = new CarePlan();
    plan.setSubject(new Reference("Patient/" + request.patientId()));
    plan.setStatus(CarePlan.CarePlanStatus.fromCode(request.status()));
    plan.setIntent(CarePlan.CarePlanIntent.PLAN);
    plan.setTitle(request.title());
    Date start = Date.from(request.periodStart().atStartOfDay(ZoneId.systemDefault()).toInstant());
    plan.setPeriod(new Period().setStart(start));
    if (request.conditionIds() != null) {
      request.conditionIds().forEach(cid -> plan.addAddresses(new Reference("Condition/" + cid)));
    }
    if (request.goalIds() != null) {
      request.goalIds().forEach(gid -> plan.addGoal(new Reference("Goal/" + gid)));
    }
    return plan;
  }

  public CarePlanResponse toResponse(CarePlan plan) {
    return new CarePlanResponse(
        FhirValues.idOf(plan),
        FhirValues.referenceId(plan.getSubject()),
        plan.getAddresses().stream().map(FhirValues::referenceId).toList(),
        plan.getTitle(),
        plan.hasStatus() ? plan.getStatus().toCode() : null,
        FhirValues.localDate(plan.getPeriod().getStartElement()),
        plan.getGoal().stream().map(FhirValues::referenceId).toList());
  }
}
