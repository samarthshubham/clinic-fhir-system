package io.github.samarthshubham.clinic.mapper;

import static io.github.samarthshubham.clinic.support.FhirDates.localDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.clinic.dto.CarePlanRequest;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

class CarePlanMapperTest {

  private static final LocalDate START = LocalDate.of(2026, 2, 1);

  private final CarePlanMapper mapper = new CarePlanMapper();

  @Test
  void mapsPlanFieldsConditionsAndGoals() {
    CarePlan plan =
        mapper.toFhir(
            new CarePlanRequest(
                "p1", List.of("c1", "c2"), "BP control", "active", START, List.of("g1")));

    assertThat(plan.getSubject().getReference()).isEqualTo("Patient/p1");
    assertThat(plan.getStatus()).isEqualTo(CarePlan.CarePlanStatus.ACTIVE);
    assertThat(plan.getIntent()).isEqualTo(CarePlan.CarePlanIntent.PLAN);
    assertThat(plan.getTitle()).isEqualTo("BP control");
    assertThat(localDate(plan.getPeriod().getStart())).isEqualTo(START);
    assertThat(plan.getAddresses())
        .extracting(Reference::getReference)
        .containsExactly("Condition/c1", "Condition/c2");
    assertThat(plan.getGoal()).extracting(Reference::getReference).containsExactly("Goal/g1");
  }

  @Test
  void unknownStatusIsRejected() {
    CarePlanRequest request = new CarePlanRequest("p1", null, "Diet", "open", START, null);

    assertThatThrownBy(() -> mapper.toFhir(request)).isInstanceOf(FHIRException.class);
  }

  @Test
  void omitsConditionsAndGoalsWhenNotGiven() {
    CarePlan plan = mapper.toFhir(new CarePlanRequest("p1", null, "Diet", "draft", START, null));

    assertThat(plan.getAddresses()).isEmpty();
    assertThat(plan.getGoal()).isEmpty();
  }
}
