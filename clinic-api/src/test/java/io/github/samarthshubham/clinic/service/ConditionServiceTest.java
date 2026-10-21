package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.dto.ConditionRequest;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.ConditionMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConditionServiceTest {

  private static final String ACTIVE =
      "clinical-status=http://terminology.hl7.org/CodeSystem/condition-clinical|active";

  private FhirClientStub fhir;
  private ConditionService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new ConditionService(fhir.client(), new ConditionMapper());
  }

  @Test
  void createStoresMappedConditionAndReturnsTheStoredCopy() {
    Condition condition =
        service.createCondition(
            new ConditionRequest(
                "p1", null, "59621000", "Essential hypertension", "active", LocalDate.now()));

    assertThat(fhir.created()).singleElement().isNotSameAs(condition);
    assertThat(condition.getIdElement().getIdPart()).isEqualTo("1");
    assertThat(condition.getCode().getCodingFirstRep().getCode()).isEqualTo("59621000");
  }

  @Test
  void getReturnsStoredCondition() {
    Condition stored = new Condition();
    fhir.givenStored(stored, "c1");

    assertThat(service.getCondition("c1").getIdElement().getIdPart()).isEqualTo("c1");
  }

  @Test
  void getUnknownConditionThrowsNotFound() {
    assertThatThrownBy(() -> service.getCondition("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Condition with id 'missing' not found");
  }

  @Test
  void getPropagatesOtherServerErrors() {
    fhir.givenReadFails(Condition.class, "x", new InternalErrorException("FHIR server error"));

    assertThatThrownBy(() -> service.getCondition("x")).isInstanceOf(InternalErrorException.class);
  }

  @Test
  void listsConditionsForPatient() {
    Bundle conditions = bundleOf(new Condition());
    fhir.givenSearchResult(Condition.class, conditions);

    assertThat(service.getConditionsForPatient("p1")).isSameAs(conditions);
    assertThat(fhir.lastSearchCriteria(Condition.class))
        .containsExactlyInAnyOrder("subject=Patient/p1");
  }

  @Test
  void listsOnlyActiveConditions() {
    service.getActiveConditionsForPatient("p1");

    assertThat(fhir.lastSearchCriteria(Condition.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", ACTIVE);
  }

  @Test
  void findsActiveConditionWithCode() {
    fhir.givenSearchResult(Condition.class, bundleOf(new Condition()));

    assertThat(service.hasActiveConditionWithCode("p1", "59621000")).isTrue();
    assertThat(fhir.searchCount(Condition.class)).isEqualTo(1);
    assertThat(fhir.lastSearchCriteria(Condition.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", "code=59621000", ACTIVE);
  }

  @Test
  void noActiveConditionWithCodeWhenSearchIsEmpty() {
    fhir.givenSearchResult(Condition.class, new Bundle());

    assertThat(service.hasActiveConditionWithCode("p1", "59621000")).isFalse();
  }
}
