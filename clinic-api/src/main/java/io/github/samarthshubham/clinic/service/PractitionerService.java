package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.dto.PractitionerRequest;
import io.github.samarthshubham.clinic.dto.PractitionerResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.PractitionerMapper;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Practitioner;
import org.springframework.stereotype.Service;

@Service
public class PractitionerService {

  static final String STAFF_ID_PREFIX = "user-";

  private final IGenericClient fhirClient;
  private final PractitionerMapper practitionerMapper;

  public PractitionerService(IGenericClient fhirClient, PractitionerMapper practitionerMapper) {
    this.fhirClient = fhirClient;
    this.practitionerMapper = practitionerMapper;
  }

  public PractitionerResponse createPractitioner(PractitionerRequest request) {
    var outcome = fhirClient.create().resource(practitionerMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    return practitionerMapper.toResponse(
        fhirClient.read().resource(Practitioner.class).withId(id).execute());
  }

  public void upsertStaffPractitioner(String username, PractitionerRequest request) {
    fhirClient
        .update()
        .resource(practitionerMapper.toStaffFhir(STAFF_ID_PREFIX + username, username, request))
        .execute();
  }

  public Optional<PractitionerResponse> findByUsername(String username) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(Practitioner.class)
            .where(
                new TokenClientParam("identifier")
                    .exactly()
                    .systemAndCode(PractitionerMapper.USERNAME_SYSTEM, username))
            .returnBundle(Bundle.class)
            .execute();
    return bundle.getEntry().stream()
        .map(entry -> (Practitioner) entry.getResource())
        .findFirst()
        .map(practitionerMapper::toResponse);
  }

  public PractitionerResponse getPractitioner(String id) {
    return practitionerMapper.toResponse(findPractitioner(id));
  }

  public Practitioner findPractitioner(String id) {
    try {
      return fhirClient.read().resource(Practitioner.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Practitioner", id);
    }
  }
}
