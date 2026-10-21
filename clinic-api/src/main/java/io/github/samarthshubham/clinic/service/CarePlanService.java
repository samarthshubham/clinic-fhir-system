package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.dto.CarePlanRequest;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.CarePlanMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CarePlan;
import org.springframework.stereotype.Service;

@Service
public class CarePlanService {

  private final IGenericClient fhirClient;
  private final CarePlanMapper carePlanMapper;

  public CarePlanService(IGenericClient fhirClient, CarePlanMapper carePlanMapper) {
    this.fhirClient = fhirClient;
    this.carePlanMapper = carePlanMapper;
  }

  public CarePlan createCarePlan(CarePlanRequest request) {
    var outcome = fhirClient.create().resource(carePlanMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    return fhirClient.read().resource(CarePlan.class).withId(id).execute();
  }

  public CarePlan getCarePlan(String id) {
    try {
      return fhirClient.read().resource(CarePlan.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("CarePlan", id);
    }
  }

  public Bundle getCarePlansForPatient(String patientId) {
    return fhirClient
        .search()
        .forResource(CarePlan.class)
        .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
        .returnBundle(Bundle.class)
        .execute();
  }

  public Bundle getActiveCarePlanForPatient(String patientId) {
    return fhirClient
        .search()
        .forResource(CarePlan.class)
        .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
        .and(new TokenClientParam("status").exactly().code("active"))
        .count(1)
        .returnBundle(Bundle.class)
        .execute();
  }
}
