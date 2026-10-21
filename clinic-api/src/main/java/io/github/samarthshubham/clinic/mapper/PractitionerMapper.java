package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.PractitionerRequest;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Practitioner;
import org.springframework.stereotype.Component;

@Component
public class PractitionerMapper {

  private static final String NPI_SYSTEM = "http://hl7.org/fhir/sid/us-npi";

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
}
