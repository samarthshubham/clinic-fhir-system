package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.shubhamforge.clinic.dto.GoalRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.GoalMapper;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Goal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GoalServiceTest {

  private FhirClientStub fhir;
  private GoalService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new GoalService(fhir.client(), new GoalMapper());
  }

  @Test
  void createStoresMappedGoalAndReturnsTheStoredCopy() {
    Goal goal =
        service.createGoal(
            new GoalRequest("p1", "Walk daily", "active", null, null, null, null, null));

    assertThat(fhir.created()).singleElement().isNotSameAs(goal);
    assertThat(goal.getIdElement().getIdPart()).isEqualTo("1");
    assertThat(goal.getDescription().getText()).isEqualTo("Walk daily");
  }

  @Test
  void getReturnsStoredGoal() {
    Goal stored = new Goal();
    fhir.givenStored(stored, "g1");

    assertThat(service.getGoal("g1").getIdElement().getIdPart()).isEqualTo("g1");
  }

  @Test
  void getUnknownGoalThrowsNotFound() {
    assertThatThrownBy(() -> service.getGoal("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("Goal with id 'missing' not found");
  }

  @Test
  void getPropagatesOtherServerErrors() {
    fhir.givenReadFails(Goal.class, "x", new InternalErrorException("FHIR server error"));

    assertThatThrownBy(() -> service.getGoal("x")).isInstanceOf(InternalErrorException.class);
  }

  @Test
  void listsGoalsForPatient() {
    Bundle goals = bundleOf(new Goal());
    fhir.givenSearchResult(Goal.class, goals);

    assertThat(service.getGoalsForPatient("p1")).isSameAs(goals);
    assertThat(fhir.lastSearchCriteria(Goal.class)).containsExactlyInAnyOrder("subject=Patient/p1");
  }

  @Test
  void goalsByIdsSkipsMissingGoalsAndKeepsOrder() {
    Goal first = new Goal();
    Goal second = new Goal();
    fhir.givenStored(first, "g1");
    fhir.givenStored(second, "g2");

    Bundle result = service.getGoalsByIds(List.of("g2", "missing", "g1"));

    assertThat(result.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
    assertThat(result.getEntry())
        .extracting(entry -> entry.getResource().getIdElement().getIdPart())
        .containsExactly("g2", "g1");
  }

  @Test
  void goalsByIdsWithNoIdsIsAnEmptyCollection() {
    assertThat(service.getGoalsByIds(null).getEntry()).isEmpty();
    assertThat(service.getGoalsByIds(List.of()).getType()).isEqualTo(Bundle.BundleType.COLLECTION);
  }
}
