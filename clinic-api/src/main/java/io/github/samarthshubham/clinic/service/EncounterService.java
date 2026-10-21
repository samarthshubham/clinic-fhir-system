package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import io.github.samarthshubham.clinic.dto.EncounterRequest;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.EncounterMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.springframework.stereotype.Service;

@Service
public class EncounterService {

  private final IGenericClient fhirClient;
  private final EncounterMapper encounterMapper;

  public EncounterService(IGenericClient fhirClient, EncounterMapper encounterMapper) {
    this.fhirClient = fhirClient;
    this.encounterMapper = encounterMapper;
  }

  public Encounter createEncounter(EncounterRequest request) {
    var outcome = fhirClient.create().resource(encounterMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    return fhirClient.read().resource(Encounter.class).withId(id).execute();
  }

  public Encounter getEncounter(String id) {
    try {
      return fhirClient.read().resource(Encounter.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Encounter", id);
    }
  }

  public Bundle getEncountersForPatient(String patientId) {
    return fhirClient
        .search()
        .forResource(Encounter.class)
        .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
        .returnBundle(Bundle.class)
        .execute();
  }
}
