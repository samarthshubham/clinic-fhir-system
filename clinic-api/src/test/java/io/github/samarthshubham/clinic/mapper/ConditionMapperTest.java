package io.github.samarthshubham.clinic.mapper;

import static io.github.samarthshubham.clinic.support.FhirDates.localDate;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.clinic.dto.ConditionRequest;
import io.github.samarthshubham.clinic.dto.ConditionResponse;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.junit.jupiter.api.Test;

class ConditionMapperTest {

  private static final LocalDate ONSET = LocalDate.of(2026, 4, 2);

  private final ConditionMapper mapper = new ConditionMapper();

  @Test
  void mapsCodeStatusOnsetAndReferences() {
    Condition condition =
        mapper.toFhir(
            new ConditionRequest(
                "p1", "e1", "59621000", "Essential hypertension", "active", ONSET));

    assertThat(condition.getSubject().getReference()).isEqualTo("Patient/p1");
    assertThat(condition.getEncounter().getReference()).isEqualTo("Encounter/e1");
    assertThat(condition.getCode().getCodingFirstRep().getCode()).isEqualTo("59621000");
    assertThat(condition.getCode().getCodingFirstRep().hasSystem()).isFalse();
    assertThat(condition.getCode().getText()).isEqualTo("Essential hypertension");
    Coding status = condition.getClinicalStatus().getCodingFirstRep();
    assertThat(status.getSystem())
        .isEqualTo("http://terminology.hl7.org/CodeSystem/condition-clinical");
    assertThat(status.getCode()).isEqualTo("active");
    assertThat(localDate(condition.getOnsetDateTimeType().getValue())).isEqualTo(ONSET);
  }

  @Test
  void omitsEncounterWhenNotGiven() {
    Condition condition =
        mapper.toFhir(new ConditionRequest("p1", null, "86290005", "Hypoxemia", "active", ONSET));

    assertThat(condition.hasEncounter()).isFalse();
  }

  @Test
  void toResponseIsTheReverseOfToFhir() {
    Condition condition =
        mapper.toFhir(
            new ConditionRequest(
                "p1", "e1", "59621000", "Essential hypertension", "active", ONSET));
    condition.setId("c1");

    assertThat(mapper.toResponse(condition))
        .isEqualTo(
            new ConditionResponse(
                "c1", "p1", "e1", "59621000", "Essential hypertension", "active", ONSET));
  }

  @Test
  void toResponseLeavesMissingFieldsNull() {
    Condition condition = new Condition();
    condition.setId("c1");

    assertThat(mapper.toResponse(condition))
        .isEqualTo(new ConditionResponse("c1", null, null, null, null, null, null));
  }
}
