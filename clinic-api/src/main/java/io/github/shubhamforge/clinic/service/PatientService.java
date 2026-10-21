package io.github.shubhamforge.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.DateClientParam;
import ca.uhn.fhir.rest.gclient.StringClientParam;
import io.github.shubhamforge.clinic.dto.PatientRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.PatientMapper;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Service;

@Service
public class PatientService {

  private final IGenericClient fhirClient;
  private final PatientMapper patientMapper;

  public PatientService(IGenericClient fhirClient, PatientMapper patientMapper) {
    this.fhirClient = fhirClient;
    this.patientMapper = patientMapper;
  }

  public Patient createPatient(PatientRequest request) {
    var outcome = fhirClient.create().resource(patientMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    return fhirClient.read().resource(Patient.class).withId(id).execute();
  }

  public Patient getPatient(String id) {
    try {
      return fhirClient.read().resource(Patient.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Patient", id);
    }
  }

  public Bundle searchPatients(String name, LocalDate dob) {
    var query = fhirClient.search().forResource(Patient.class).returnBundle(Bundle.class);
    if (name != null && !name.isBlank()) {
      query = query.where(new StringClientParam("name").matches().value(name));
    }
    if (dob != null) {
      query = query.where(new DateClientParam("birthdate").exactly().day(dob.toString()));
    }
    return query.execute();
  }

  public boolean hasPhoneNumber(String id) {
    Patient patient = getPatient(id);
    return patient.getTelecom().stream()
        .anyMatch(t -> t.getSystem() == ContactPoint.ContactPointSystem.EMAIL);
  }
}
