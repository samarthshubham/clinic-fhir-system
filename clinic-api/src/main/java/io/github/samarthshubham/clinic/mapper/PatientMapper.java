package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.PatientRequest;
import io.github.samarthshubham.clinic.dto.PatientResponse;
import io.github.samarthshubham.clinic.dto.PatientSummary;
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

  public PatientResponse toResponse(Patient patient) {
    return new PatientResponse(
        FhirValues.idOf(patient),
        FhirValues.given(patient.getName()),
        FhirValues.family(patient.getName()),
        birthDate(patient),
        gender(patient),
        FhirValues.telecom(patient.getTelecom(), ContactPoint.ContactPointSystem.PHONE),
        FhirValues.telecom(patient.getTelecom(), ContactPoint.ContactPointSystem.EMAIL),
        patient.hasActive() ? patient.getActive() : null);
  }

  public PatientSummary toSummary(Patient patient) {
    PatientResponse response = toResponse(patient);
    return new PatientSummary(
        response.id(),
        response.firstName(),
        response.lastName(),
        response.dateOfBirth(),
        response.gender(),
        response.id(),
        response.phone(),
        response.email());
  }

  private static String birthDate(Patient patient) {
    return patient.hasBirthDate() ? patient.getBirthDateElement().getValueAsString() : null;
  }

  private static String gender(Patient patient) {
    return patient.hasGender() ? patient.getGender().toCode() : null;
  }
}
