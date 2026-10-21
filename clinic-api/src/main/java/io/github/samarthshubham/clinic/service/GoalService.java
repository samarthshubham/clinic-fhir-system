package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import io.github.samarthshubham.clinic.dto.GoalRequest;
import io.github.samarthshubham.clinic.dto.GoalResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.GoalMapper;
import io.github.samarthshubham.clinic.mapper.PageMapper;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Goal;
import org.springframework.stereotype.Service;

@Service
public class GoalService {

  private final IGenericClient fhirClient;
  private final GoalMapper goalMapper;

  public GoalService(IGenericClient fhirClient, GoalMapper goalMapper) {
    this.fhirClient = fhirClient;
    this.goalMapper = goalMapper;
  }

  public GoalResponse createGoal(GoalRequest request) {
    var outcome = fhirClient.create().resource(goalMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    return goalMapper.toResponse(fhirClient.read().resource(Goal.class).withId(id).execute());
  }

  public Goal getGoal(String id) {
    try {
      return fhirClient.read().resource(Goal.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Goal", id);
    }
  }

  public PageResponse<GoalResponse> getGoalsForPatient(String patientId) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(Goal.class)
            .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
            .returnBundle(Bundle.class)
            .execute();
    return PageMapper.toPage(bundle, Goal.class, goalMapper::toResponse);
  }

  public Bundle getGoalsByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      Bundle empty = new Bundle();
      empty.setType(Bundle.BundleType.COLLECTION);
      return empty;
    }
    Bundle result = new Bundle();
    result.setType(Bundle.BundleType.COLLECTION);
    ids.forEach(
        id -> {
          try {
            Goal goal = getGoal(id);
            result.addEntry().setResource(goal);
          } catch (ResourceNotFoundException e) {
            // skip missing goals
          }
        });
    return result;
  }
}
