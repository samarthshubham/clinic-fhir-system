package io.github.samarthshubham.clinic.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.samarthshubham.clinic.dto.PatientRequest;
import java.time.LocalDate;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class PatientMapperTest {

  private final PatientMapper mapper = new PatientMapper();

  @Test
  void mapsNameBirthDateGenderAndContacts() {
    Patient patient =
        mapper.toFhir(
            new PatientRequest(
                "Priya", "Nair", LocalDate.of(1985, 3, 14), "female", "+1-555-0100", "p@x.org"));

    assertThat(patient.getNameFirstRep().getFamily()).isEqualTo("Nair");
    assertThat(patient.getNameFirstRep().getGivenAsSingleString()).isEqualTo("Priya");
    assertThat(patient.getBirthDateElement().getValueAsString()).isEqualTo("1985-03-14");
    assertThat(patient.getGender()).isEqualTo(AdministrativeGender.FEMALE);
    assertThat(patient.getTelecom())
        .extracting(ContactPoint::getSystem, ContactPoint::getValue)
        .containsExactly(
            tuple(ContactPoint.ContactPointSystem.PHONE, "+1-555-0100"),
            tuple(ContactPoint.ContactPointSystem.EMAIL, "p@x.org"));
  }

  @Test
  void genderIsCaseInsensitive() {
    Patient patient =
        mapper.toFhir(
            new PatientRequest("Tony", "Stark", LocalDate.of(1970, 5, 29), "MALE", null, null));

    assertThat(patient.getGender()).isEqualTo(AdministrativeGender.MALE);
  }

  @Test
  void omitsTelecomWhenNoContactsGiven() {
    Patient patient =
        mapper.toFhir(
            new PatientRequest("Tony", "Stark", LocalDate.of(1970, 5, 29), "male", null, null));

    assertThat(patient.getTelecom()).isEmpty();
  }

  @Test
  void onlyEmailIsMappedAsEmail() {
    Patient patient =
        mapper.toFhir(
            new PatientRequest(
                "Tony", "Stark", LocalDate.of(1970, 5, 29), "male", null, "t@x.org"));

    assertThat(patient.getTelecom())
        .extracting(ContactPoint::getSystem)
        .containsExactly(ContactPoint.ContactPointSystem.EMAIL);
  }

  @Test
  void onlyPhoneIsMappedAsPhone() {
    Patient patient =
        mapper.toFhir(
            new PatientRequest("Tony", "Stark", LocalDate.of(1970, 5, 29), "male", "555", null));

    assertThat(patient.getTelecom())
        .extracting(ContactPoint::getSystem)
        .containsExactly(ContactPoint.ContactPointSystem.PHONE);
  }

  @Test
  void unknownGenderIsRejected() {
    PatientRequest request =
        new PatientRequest("Tony", "Stark", LocalDate.of(1970, 5, 29), "robot", null, null);

    assertThatThrownBy(() -> mapper.toFhir(request)).isInstanceOf(FHIRException.class);
  }
}
