package io.github.shubhamforge.clinic.mapper;

import static io.github.shubhamforge.clinic.support.FhirDates.localDate;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.shubhamforge.clinic.dto.GoalRequest;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Goal;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.Test;

class GoalMapperTest {

  private static final LocalDate DUE = LocalDate.of(2026, 9, 30);

  private final GoalMapper mapper = new GoalMapper();

  @Test
  void mapsGoalWithMeasurableTarget() {
    Goal goal =
        mapper.toFhir(
            new GoalRequest(
                "p1",
                "Lower systolic BP",
                "active",
                "8480-6",
                "Systolic blood pressure",
                130.0,
                "mm[Hg]",
                DUE));

    assertThat(goal.getSubject().getReference()).isEqualTo("Patient/p1");
    assertThat(goal.getLifecycleStatus()).isEqualTo(Goal.GoalLifecycleStatus.ACTIVE);
    assertThat(goal.getDescription().getText()).isEqualTo("Lower systolic BP");
    Goal.GoalTargetComponent target = goal.getTargetFirstRep();
    Coding measure = target.getMeasure().getCodingFirstRep();
    assertThat(measure.getSystem()).isEqualTo("http://loinc.org");
    assertThat(measure.getCode()).isEqualTo("8480-6");
    assertThat(measure.getDisplay()).isEqualTo("Systolic blood pressure");
    Quantity detail = target.getDetailQuantity();
    assertThat(detail.getValue().doubleValue()).isEqualTo(130.0);
    assertThat(detail.getUnit()).isEqualTo("mm[Hg]");
    assertThat(detail.getSystem()).isEqualTo("http://unitsofmeasure.org");
    assertThat(detail.getCode()).isEqualTo("mm[Hg]");
    assertThat(localDate(target.getDueDateType().getValue())).isEqualTo(DUE);
  }

  @Test
  void targetWithoutDueDateHasNoDue() {
    Goal goal =
        mapper.toFhir(
            new GoalRequest("p1", "Reach 75 kg", "active", "29463-7", "Weight", 75.0, "kg", null));

    assertThat(goal.getTargetFirstRep().hasDue()).isFalse();
  }

  @Test
  void noTargetWithoutMeasureCodeOrValue() {
    Goal withoutValue =
        mapper.toFhir(
            new GoalRequest("p1", "Walk daily", "proposed", "29463-7", null, null, null, DUE));
    Goal withoutCode =
        mapper.toFhir(new GoalRequest("p1", "Walk daily", "proposed", null, null, 5.0, "km", DUE));

    assertThat(withoutValue.getTarget()).isEmpty();
    assertThat(withoutCode.getTarget()).isEmpty();
  }
}
