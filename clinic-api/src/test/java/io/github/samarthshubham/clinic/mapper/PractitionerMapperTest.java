package io.github.samarthshubham.clinic.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.clinic.dto.PractitionerRequest;
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
}
