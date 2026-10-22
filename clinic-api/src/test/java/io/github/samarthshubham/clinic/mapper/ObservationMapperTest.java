package io.github.samarthshubham.clinic.mapper;

import static io.github.samarthshubham.clinic.support.FhirDates.localDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.samarthshubham.clinic.dto.ObservationResponse;
import io.github.samarthshubham.clinic.dto.VitalsRequest;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Timing;
import org.junit.jupiter.api.Test;

class ObservationMapperTest {

  private static final LocalDate MEASURED = LocalDate.of(2026, 5, 20);

  private final ObservationMapper mapper = new ObservationMapper();

  @Test
  void mapsEachVitalToItsLoincCodeDisplayAndUnit() {
    List<Observation> observations =
        mapper.toFhirObservations(
            new VitalsRequest("p1", "e1", MEASURED, 150, 95, 80.5, 93.0, 72, 37.1));

    assertThat(observations)
        .extracting(
            obs -> obs.getCode().getCodingFirstRep().getCode(),
            obs -> obs.getCode().getCodingFirstRep().getDisplay(),
            obs -> obs.getValueQuantity().getValue().doubleValue(),
            obs -> obs.getValueQuantity().getUnit(),
            obs -> obs.getValueQuantity().getCode())
        .containsExactly(
            tuple("8480-6", "Systolic blood pressure", 150.0, "mm[Hg]", "mm[Hg]"),
            tuple("8462-4", "Diastolic blood pressure", 95.0, "mm[Hg]", "mm[Hg]"),
            tuple("29463-7", "Body weight", 80.5, "kg", "kg"),
            tuple("59408-5", "Oxygen saturation", 93.0, "%", "%"),
            tuple("8867-4", "Heart rate", 72.0, "/min", "/min"),
            tuple("8310-5", "Body temperature", 37.1, "Cel", "Cel"));
  }

  @Test
  void everyObservationIsFinalWithLoincUcumSubjectEncounterAndDate() {
    List<Observation> observations =
        mapper.toFhirObservations(
            new VitalsRequest("p1", "e1", MEASURED, 150, 95, 80.5, 93.0, 72, 37.1));

    assertThat(observations)
        .hasSize(6)
        .allSatisfy(
            obs -> {
              assertThat(obs.getStatus()).isEqualTo(Observation.ObservationStatus.FINAL);
              assertThat(obs.getCode().getCodingFirstRep().getSystem())
                  .isEqualTo("http://loinc.org");
              assertThat(obs.getValueQuantity().getSystem()).isEqualTo("http://unitsofmeasure.org");
              assertThat(obs.getSubject().getReference()).isEqualTo("Patient/p1");
              assertThat(obs.getEncounter().getReference()).isEqualTo("Encounter/e1");
              assertThat(localDate(obs.getEffectiveDateTimeType().getValue())).isEqualTo(MEASURED);
            });
  }

  @Test
  void omitsEncounterWhenNotGiven() {
    List<Observation> observations =
        mapper.toFhirObservations(
            new VitalsRequest("p1", null, MEASURED, null, null, 80.0, null, null, null));

    assertThat(observations)
        .singleElement()
        .satisfies(obs -> assertThat(obs.hasEncounter()).isFalse());
  }

  @Test
  void noObservationsWhenNoVitalsGiven() {
    assertThat(
            mapper.toFhirObservations(
                new VitalsRequest("p1", null, MEASURED, null, null, null, null, null, null)))
        .isEmpty();
  }

  @Test
  void toResponseResolvesTheVitalTypeFromTheLoincCode() {
    Observation observation =
        mapper
            .toFhirObservations(
                new VitalsRequest("p1", "e1", MEASURED, null, null, 80.5, null, null, null))
            .get(0);
    observation.setEffective(new DateTimeType("2026-05-20T09:30:00-04:00"));
    observation.setId("o1");

    assertThat(mapper.toResponse(observation))
        .isEqualTo(
            new ObservationResponse(
                "o1", "p1", "e1", "weight", "29463-7", 80.5, "kg", "2026-05-20T09:30:00-04:00"));
  }

  @Test
  void toResponseKeepsUnknownCodesWithoutAType() {
    Observation observation = new Observation();
    observation.getCode().addCoding().setSystem("http://snomed.info/sct").setCode("x");
    observation.getCode().addCoding().setSystem("http://loinc.org").setCode("2345-7");
    observation.setId("o1");

    assertThat(mapper.toResponse(observation))
        .isEqualTo(new ObservationResponse("o1", null, null, null, "2345-7", null, null, null));
  }

  @Test
  void toResponseReadsTheStartOfAnEffectivePeriod() {
    Observation observation = new Observation();
    observation.setEffective(
        new Period()
            .setStartElement(new DateTimeType("2026-05-01T22:00:00-05:00"))
            .setEndElement(new DateTimeType("2026-05-01T22:15:00-05:00")));
    observation.setId("o1");

    assertThat(mapper.toResponse(observation).effectiveDateTime())
        .isEqualTo("2026-05-01T22:00:00-05:00");
  }

  @Test
  void toResponseReadsAnEffectiveInstant() {
    Observation observation = new Observation();
    observation.setEffective(new InstantType("2026-05-01T22:00:00.000-05:00"));
    observation.setId("o1");

    assertThat(mapper.toResponse(observation).effectiveDateTime())
        .isEqualTo("2026-05-01T22:00:00.000-05:00");
  }

  @Test
  void toResponseLeavesEffectiveNullForOtherTypes() {
    Observation observation = new Observation();
    observation.setEffective(new Timing());
    observation.setId("o1");

    assertThat(mapper.toResponse(observation).effectiveDateTime()).isNull();
  }

  @Test
  void patientReportedObservationsNameThePatientAsPerformer() {
    Observation reported =
        mapper
            .toFhirObservations(
                new VitalsRequest(
                    "p1", null, LocalDate.of(2026, 10, 22), 185, null, null, null, null, null),
                true)
            .get(0);
    Observation clinicReading =
        mapper
            .toFhirObservations(
                new VitalsRequest(
                    "p1", null, LocalDate.of(2026, 10, 22), 185, null, null, null, null, null))
            .get(0);

    assertThat(ObservationMapper.isPatientReported(reported)).isTrue();
    assertThat(ObservationMapper.isPatientReported(clinicReading)).isFalse();
  }
}
