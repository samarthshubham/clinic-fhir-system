package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.dto.ConditionRequest;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.ConditionMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.springframework.stereotype.Service;

@Service
public class ConditionService {

  private static final String CLINICAL_STATUS_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/condition-clinical";

  private final IGenericClient fhirClient;
  private final ConditionMapper conditionMapper;

  public ConditionService(IGenericClient fhirClient, ConditionMapper conditionMapper) {
    this.fhirClient = fhirClient;
    this.conditionMapper = conditionMapper;
  }

  public Condition createCondition(ConditionRequest request) {
    var outcome = fhirClient.create().resource(conditionMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    return fhirClient.read().resource(Condition.class).withId(id).execute();
  }

  public Condition getCondition(String id) {
    try {
      return fhirClient.read().resource(Condition.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Condition", id);
    }
  }

  public Bundle getConditionsForPatient(String patientId) {
    return fhirClient
        .search()
        .forResource(Condition.class)
        .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
        .returnBundle(Bundle.class)
        .execute();
  }

  public Bundle getActiveConditionsForPatient(String patientId) {
    return fhirClient
        .search()
        .forResource(Condition.class)
        .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
        .and(
            new TokenClientParam("clinical-status")
                .exactly()
                .systemAndCode(CLINICAL_STATUS_SYSTEM, "active"))
        .returnBundle(Bundle.class)
        .execute();
  }

  public boolean hasActiveConditionWithCode(String patientId, String code) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(Condition.class)
            .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
            .and(new TokenClientParam("code").exactly().code(code))
            .and(
                new TokenClientParam("clinical-status")
                    .exactly()
                    .systemAndCode(CLINICAL_STATUS_SYSTEM, "active"))
            .returnBundle(Bundle.class)
            .execute();
    return bundle.getEntry() != null && !bundle.getEntry().isEmpty();
  }
}
