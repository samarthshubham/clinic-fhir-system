package io.github.shubhamforge.clinic.mapper;

import static io.github.shubhamforge.clinic.support.FhirDates.localDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.shubhamforge.clinic.dto.VitalsRequest;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.Observation;
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
            obs -> obs.getValueQuantity().getCode())
        .containsExactly(
            tuple("8480-6", "Systolic blood pressure", 150.0, "mm[Hg]"),
            tuple("8462-4", "Diastolic blood pressure", 95.0, "mm[Hg]"),
            tuple("29463-7", "Body weight", 80.5, "kg"),
            tuple("59408-5", "Oxygen saturation", 93.0, "%"),
            tuple("8867-4", "Heart rate", 72.0, "/min"),
            tuple("8310-5", "Body temperature", 37.1, "Cel"));
  }

  @Test
  void everyObservationIsFinalWithLoincUcumSubjectEncounterAndDate() {
    List<Observation> observations =
        mapper.toFhirObservations(
            new VitalsRequest("p1", "e1", MEASURED, 150, null, null, 93.0, null, null));

    assertThat(observations)
        .hasSize(2)
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
}
