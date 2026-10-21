package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.PatientRequest;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class PatientMapper {

  public Patient toFhir(PatientRequest request) {
    Patient patient = new Patient();
    patient.addName().setFamily(request.lastName()).addGiven(request.firstName());
    patient.setBirthDateElement(new DateType(request.dateOfBirth().toString()));
    patient.setGender(Enumerations.AdministrativeGender.fromCode(request.gender().toLowerCase()));
    if (request.phone() != null) {
      patient
          .addTelecom()
          .setSystem(ContactPoint.ContactPointSystem.PHONE)
          .setValue(request.phone());
    }
    if (request.email() != null) {
      patient
          .addTelecom()
          .setSystem(ContactPoint.ContactPointSystem.EMAIL)
          .setValue(request.email());
    }
    return patient;
  }
}
