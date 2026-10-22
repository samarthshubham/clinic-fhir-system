package io.github.samarthshubham.clinic.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.clinic.dto.PractitionerRequest;
import io.github.samarthshubham.clinic.dto.PractitionerResponse;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Practitioner;
import org.junit.jupiter.api.Test;

class PractitionerMapperTest {

  private final PractitionerMapper mapper = new PractitionerMapper();

  @Test
  void mapsNameSpecialtyNpiAndEmail() {
    Practitioner practitioner =
        mapper.toFhir(
            new PractitionerRequest("Gregory", "House", "Diagnostics", "1234567890", "g@x.org"));

    assertThat(practitioner.getNameFirstRep().getFamily()).isEqualTo("House");
    assertThat(practitioner.getNameFirstRep().getGivenAsSingleString()).isEqualTo("Gregory");
    assertThat(practitioner.getQualificationFirstRep().getCode().getText())
        .isEqualTo("Diagnostics");
    Identifier npi = practitioner.getIdentifierFirstRep();
    assertThat(npi.getSystem()).isEqualTo("http://hl7.org/fhir/sid/us-npi");
    assertThat(npi.getValue()).isEqualTo("1234567890");
    assertThat(npi.getUse()).isEqualTo(Identifier.IdentifierUse.OFFICIAL);
    assertThat(practitioner.getTelecomFirstRep().getSystem())
        .isEqualTo(ContactPoint.ContactPointSystem.EMAIL);
    assertThat(practitioner.getTelecomFirstRep().getValue()).isEqualTo("g@x.org");
  }

  @Test
  void omitsIdentifierAndTelecomWhenNotGiven() {
    Practitioner practitioner =
        mapper.toFhir(new PractitionerRequest("Gregory", "House", "Diagnostics", null, null));

    assertThat(practitioner.getIdentifier()).isEmpty();
    assertThat(practitioner.getTelecom()).isEmpty();
  }

  @Test
  void toResponseFlattensTheStoredPractitioner() {
    Practitioner practitioner =
        mapper.toFhir(
            new PractitionerRequest("Gregory", "House", "Diagnostics", "1234567890", "g@x.org"));
    practitioner.addIdentifier().setSystem("urn:other").setValue("other");
    practitioner.addTelecom().setSystem(ContactPoint.ContactPointSystem.PHONE).setValue("555");
    practitioner.setId("dr1");

    assertThat(mapper.toResponse(practitioner))
        .isEqualTo(
            new PractitionerResponse(
                "dr1", "Gregory", "House", "Diagnostics", "1234567890", "g@x.org"));
  }

  @Test
  void toResponseLeavesMissingFieldsNull() {
    Practitioner practitioner = new Practitioner();
    practitioner.addIdentifier().setSystem("urn:other").setValue("other");
    practitioner.setId("dr1");

    assertThat(mapper.toResponse(practitioner))
        .isEqualTo(new PractitionerResponse("dr1", null, null, null, null, null));
  }

  @Test
  void staffPractitionerCarriesFixedIdAndUsernameIdentifier() {
    Practitioner practitioner =
        mapper.toStaffFhir(
            "user-clinician",
            "clinician",
            new PractitionerRequest("Anika", "Patel", "Internal Medicine", null, null));

    assertThat(practitioner.getIdElement().getIdPart()).isEqualTo("user-clinician");
    Identifier username = practitioner.getIdentifierFirstRep();
    assertThat(username.getSystem()).isEqualTo(PractitionerMapper.USERNAME_SYSTEM);
    assertThat(username.getValue()).isEqualTo("clinician");
    assertThat(practitioner.getQualificationFirstRep().getCode().getText())
        .isEqualTo("Internal Medicine");
  }
}
