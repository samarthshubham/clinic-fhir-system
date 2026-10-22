package io.github.samarthshubham.clinic.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.clinic.dto.AlertResponse;
import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.hl7.fhir.r4.model.Flag;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

class FlagMapperTest {

  private static final ClinicianAlert ALERT =
      new ClinicianAlert("Patient/p1", "Observation/obs-9", "Systolic BP above 180 mmHg");
  private static final Instant RAISED_AT = Instant.parse("2026-10-22T10:15:00Z");

  private final FlagMapper mapper = new FlagMapper();

  @Test
  void flagReferencesPatientAndObservationWithActiveStatus() {
    Flag flag = mapper.toFhir(ALERT, RAISED_AT);

    assertThat(flag.getStatus()).isEqualTo(Flag.FlagStatus.ACTIVE);
    assertThat(flag.getSubject().getReference()).isEqualTo("Patient/p1");
    assertThat(
            ((Reference) flag.getExtensionByUrl(FlagMapper.FLAG_DETAIL_URL).getValue())
                .getReference())
        .isEqualTo("Observation/obs-9");
    assertThat(flag.getIdentifierFirstRep().getSystem())
        .isEqualTo(FlagMapper.ALERT_IDENTIFIER_SYSTEM);
    assertThat(flag.getIdentifierFirstRep().getValue()).isEqualTo("obs-9");
    assertThat(flag.getCode().getCodingFirstRep().getCode()).isEqualTo(FlagMapper.HIGH_SYSTOLIC_BP);
    assertThat(flag.getCode().getText()).isEqualTo("Systolic BP above 180 mmHg");
    assertThat(flag.getCategoryFirstRep().getCodingFirstRep().getCode()).isEqualTo("clinical");
    assertThat(flag.getPeriod().getStart().toInstant()).isEqualTo(RAISED_AT);
  }

  @Test
  void storedFlagCarriesNoPatientDetailsOrReadingValues() {
    String json =
        FhirContext.forR4Cached()
            .newJsonParser()
            .encodeResourceToString(mapper.toFhir(ALERT, RAISED_AT));

    assertThat(json)
        .doesNotContain("\"name\"", "\"valueQuantity\"", "\"birthDate\"", "\"telecom\"")
        .contains("Patient/p1", "Observation/obs-9");
  }

  @Test
  void responseFlattensReferencesToIds() {
    Flag flag = mapper.toFhir(ALERT, RAISED_AT);
    flag.setId("f1");

    assertThat(mapper.toResponse(flag))
        .isEqualTo(
            new AlertResponse(
                "f1",
                "p1",
                "obs-9",
                "Systolic BP above 180 mmHg",
                OffsetDateTime.ofInstant(
                    RAISED_AT, flag.getPeriod().getStartElement().getTimeZone().toZoneId()),
                "active"));
  }

  @Test
  void responseToleratesMissingDetailAndStatus() {
    Flag flag = new Flag();
    flag.setId("f2");
    flag.setSubject(new Reference("Patient/p2"));

    assertThat(mapper.toResponse(flag))
        .isEqualTo(new AlertResponse("f2", "p2", null, null, null, null));
  }

  @Test
  void alertKeyIsTheObservationId() {
    assertThat(mapper.alertKey(ALERT)).isEqualTo("obs-9");
  }
}
