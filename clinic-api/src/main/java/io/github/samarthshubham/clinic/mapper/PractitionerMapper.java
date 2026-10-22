package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.PractitionerRequest;
import io.github.samarthshubham.clinic.dto.PractitionerResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Practitioner;
import org.springframework.stereotype.Component;

@Component
public class PractitionerMapper {

  private static final String NPI_SYSTEM = "http://hl7.org/fhir/sid/us-npi";
  public static final String USERNAME_SYSTEM = "http://praxis.clinic/fhir/identifier/username";

  public Practitioner toFhir(PractitionerRequest request) {
    Practitioner practitioner = new Practitioner();
    practitioner.addName().setFamily(request.lastName()).addGiven(request.firstName());
    practitioner.addQualification().setCode(new CodeableConcept().setText(request.specialty()));
    if (request.npi() != null) {
      practitioner
          .addIdentifier()
          .setSystem(NPI_SYSTEM)
          .setValue(request.npi())
          .setUse(Identifier.IdentifierUse.OFFICIAL);
    }
    if (request.email() != null) {
      practitioner
          .addTelecom()
          .setSystem(ContactPoint.ContactPointSystem.EMAIL)
          .setValue(request.email());
    }
    return practitioner;
  }

  public Practitioner toStaffFhir(String id, String username, PractitionerRequest request) {
    Practitioner practitioner = toFhir(request);
    practitioner.setId(id);
    practitioner
        .addIdentifier()
        .setSystem(USERNAME_SYSTEM)
        .setValue(username)
        .setUse(Identifier.IdentifierUse.SECONDARY);
    return practitioner;
  }

  public PractitionerResponse toResponse(Practitioner practitioner) {
    String specialty =
        practitioner.hasQualification()
            ? practitioner.getQualificationFirstRep().getCode().getText()
            : null;
    String npi =
        practitioner.getIdentifier().stream()
            .filter(identifier -> NPI_SYSTEM.equals(identifier.getSystem()))
            .map(Identifier::getValue)
            .findFirst()
            .orElse(null);
    return new PractitionerResponse(
        FhirValues.idOf(practitioner),
        FhirValues.given(practitioner.getName()),
        FhirValues.family(practitioner.getName()),
        specialty,
        npi,
        FhirValues.telecom(practitioner.getTelecom(), ContactPoint.ContactPointSystem.EMAIL));
  }
}
