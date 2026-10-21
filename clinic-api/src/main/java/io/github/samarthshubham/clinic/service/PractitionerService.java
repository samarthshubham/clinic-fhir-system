package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.github.samarthshubham.clinic.dto.PractitionerRequest;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.PractitionerMapper;
import org.hl7.fhir.r4.model.Practitioner;
import org.springframework.stereotype.Service;

@Service
public class PractitionerService {

  private final IGenericClient fhirClient;
  private final PractitionerMapper practitionerMapper;

  public PractitionerService(IGenericClient fhirClient, PractitionerMapper practitionerMapper) {
    this.fhirClient = fhirClient;
    this.practitionerMapper = practitionerMapper;
  }

  public Practitioner createPractitioner(PractitionerRequest request) {
    var outcome = fhirClient.create().resource(practitionerMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    return fhirClient.read().resource(Practitioner.class).withId(id).execute();
  }

  public Practitioner getPractitioner(String id) {
    try {
      return fhirClient.read().resource(Practitioner.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Practitioner", id);
    }
  }
}
