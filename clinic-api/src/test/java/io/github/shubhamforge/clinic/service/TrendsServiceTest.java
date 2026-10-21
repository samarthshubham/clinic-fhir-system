package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static io.github.shubhamforge.clinic.support.FhirClientStub.quantityObservation;
import static io.github.shubhamforge.clinic.support.FhirClientStub.vital;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.shubhamforge.clinic.config.ClinicalThresholds;
import io.github.shubhamforge.clinic.dto.BpSeries;
import io.github.shubhamforge.clinic.dto.DataPoint;
import io.github.shubhamforge.clinic.dto.SimpleSeries;
import io.github.shubhamforge.clinic.dto.TrendsResponse;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class TrendsServiceTest {

  private static final LocalDate DAY = LocalDate.of(2026, 5, 1);
  private static final String SYSTOLIC = "8480-6";
  private static final String DIASTOLIC = "8462-4";
  private static final String WEIGHT = "29463-7";
  private static final String SPO2 = "59408-5";

  private FhirClientStub fhir;
  private TrendsService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service =
        new TrendsService(fhir.client(), new ClinicalThresholds(140, 180, 90, 120, 95.0, 90.0));
  }

  @Test
  void defaultsToBloodPressureOverThirtyDays() {
    LocalDate before = LocalDate.now();

    TrendsResponse trends = service.getTrends("p1", null, null);

    assertThat(trends.period()).isEqualTo("30d");
    assertThat(LocalDate.parse(trends.to())).isBetween(before, LocalDate.now());
    assertThat(LocalDate.parse(trends.from()))
        .isEqualTo(LocalDate.parse(trends.to()).minusDays(30));
    assertThat(trends.series()).containsOnlyKeys("bp");
  }

  @ParameterizedTest
  @CsvSource({"7d,7", "30d,30", "90d,90", "1y,365"})
  void periodSetsTheWindowStart(String period, int days) {
    TrendsResponse trends = service.getTrends("p1", "weight", period);

    LocalDate to = LocalDate.parse(trends.to());
    LocalDate expectedFrom = "1y".equals(period) ? to.minusYears(1) : to.minusDays(days);
    assertThat(trends.period()).isEqualTo(period);
    assertThat(trends.from()).isEqualTo(expectedFrom.toString());
    assertThat(fhir.lastSearchCriteria(Observation.class)).contains("date=ge" + trends.from());
  }

  @Test
  void unknownPeriodFallsBackToThirtyDays() {
    TrendsResponse trends = service.getTrends("p1", "weight", "6m");

    assertThat(LocalDate.parse(trends.from()))
        .isEqualTo(LocalDate.parse(trends.to()).minusDays(30));
  }

  @Test
  void bloodPressurePairsSystolicAndDiastolicWithReferenceRange() {
    givenSeries(SYSTOLIC, vital(SYSTOLIC, 150, DAY), vital(SYSTOLIC, 140, DAY.plusDays(1)));
    givenSeries(DIASTOLIC, vital(DIASTOLIC, 95, DAY));

    BpSeries bp = (BpSeries) service.getTrends("p1", "bp", "30d").series().get("bp");

    assertThat(bp.systolic())
        .containsExactly(new DataPoint("2026-05-01", 150.0), new DataPoint("2026-05-02", 140.0));
    assertThat(bp.diastolic()).containsExactly(new DataPoint("2026-05-01", 95.0));
    assertThat(bp.referenceRange()).isEqualTo(Map.of("systolicMax", 140, "diastolicMax", 90));
  }

  @Test
  void eachSeriesQueriesThisPatientsReadingsOldestFirst() {
    TrendsResponse trends = service.getTrends("p1", "bp", "30d");

    assertThat(fhir.searches(Observation.class))
        .containsExactlyInAnyOrder(
            List.of(
                "subject=Patient/p1",
                "code=http://loinc.org|" + SYSTOLIC,
                "date=ge" + trends.from(),
                "_sort=date"),
            List.of(
                "subject=Patient/p1",
                "code=http://loinc.org|" + DIASTOLIC,
                "date=ge" + trends.from(),
                "_sort=date"));
  }

  @Test
  void spo2SeriesHasPercentUnitAndMinimumReference() {
    givenSeries(SPO2, vital(SPO2, 93, DAY));

    SimpleSeries spo2 = (SimpleSeries) service.getTrends("p1", "spo2", "30d").series().get("spo2");

    assertThat(spo2.values()).containsExactly(new DataPoint("2026-05-01", 93.0));
    assertThat(spo2.unit()).isEqualTo("%");
    assertThat(spo2.referenceRange()).isEqualTo(Map.of("min", 95.0));
  }

  @Test
  void weightSeriesHasKilogramUnitAndNoReference() {
    SimpleSeries weight =
        (SimpleSeries) service.getTrends("p1", "weight", "30d").series().get("weight");

    assertThat(weight.unit()).isEqualTo("kg");
    assertThat(weight.referenceRange()).isNull();
  }

  @Test
  void typesAreTrimmedCaseInsensitiveAndUnknownOnesIgnored() {
    TrendsResponse trends = service.getTrends("p1", " BP , SpO2 ,bmi", "30d");

    assertThat(trends.series()).containsOnlyKeys("bp", "spo2");
  }

  @Test
  void readingsWithoutDateOrNumericValueAreSkipped() {
    Observation noDate = quantityObservation(80);
    noDate.getCode().addCoding().setSystem("http://loinc.org").setCode(WEIGHT);
    Observation text = vital(WEIGHT, 0, DAY.plusDays(1)).setValue(new StringType("n/a"));
    givenSeries(WEIGHT, vital(WEIGHT, 81, DAY), noDate, text);

    SimpleSeries weight =
        (SimpleSeries) service.getTrends("p1", "weight", "30d").series().get("weight");

    assertThat(weight.values()).containsExactly(new DataPoint("2026-05-01", 81.0));
  }

  @ParameterizedTest
  @ValueSource(ints = {31, 45, 59, 60, 89, 90, 365})
  void longSeriesAreDownsampledToAtMostThirtyPoints(int readings) {
    givenDailySeries(readings);

    SimpleSeries weight =
        (SimpleSeries) service.getTrends("p1", "weight", "1y").series().get("weight");

    assertThat(weight.values()).hasSizeLessThanOrEqualTo(30).hasSizeGreaterThanOrEqualTo(15);
    assertThat(weight.values().get(0)).isEqualTo(new DataPoint(DAY.toString(), 0.0));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 29, 30})
  void shortSeriesAreKeptWhole(int readings) {
    givenDailySeries(readings);

    SimpleSeries weight =
        (SimpleSeries) service.getTrends("p1", "weight", "1y").series().get("weight");

    assertThat(weight.values()).hasSize(readings);
  }

  private void givenSeries(String loincCode, Resource... readings) {
    fhir.givenSearchResultWhen(
        Observation.class, bundleOf(readings), "code=http://loinc.org|" + loincCode);
  }

  private void givenDailySeries(int readings) {
    Resource[] observations = new Resource[readings];
    for (int i = 0; i < readings; i++) {
      observations[i] = vital(WEIGHT, i, DAY.plusDays(i));
    }
    givenSeries(WEIGHT, observations);
  }
}
