package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.dto.MedicationRequest;
import io.github.samarthshubham.clinic.dto.MedicationResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.MedicationMapper;
import io.github.samarthshubham.clinic.mapper.PageMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.springframework.stereotype.Service;

@Service
public class MedicationService {

  private final IGenericClient fhirClient;
  private final MedicationMapper medicationMapper;

  public MedicationService(IGenericClient fhirClient, MedicationMapper medicationMapper) {
    this.fhirClient = fhirClient;
    this.medicationMapper = medicationMapper;
  }

  public MedicationResponse createMedication(MedicationRequest request) {
    var outcome = fhirClient.create().resource(medicationMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    return medicationMapper.toResponse(
        fhirClient.read().resource(MedicationStatement.class).withId(id).execute());
  }

  public MedicationStatement getMedication(String id) {
    try {
      return fhirClient.read().resource(MedicationStatement.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("MedicationStatement", id);
    }
  }

  public PageResponse<MedicationResponse> getMedicationsForPatient(String patientId) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(MedicationStatement.class)
            .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
            .returnBundle(Bundle.class)
            .execute();
    return PageMapper.toPage(bundle, MedicationStatement.class, medicationMapper::toResponse);
  }

  public Bundle getActiveMedicationsForPatient(String patientId) {
    return fhirClient
        .search()
        .forResource(MedicationStatement.class)
        .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
        .and(new TokenClientParam("status").exactly().code("active"))
        .returnBundle(Bundle.class)
        .execute();
  }
}
