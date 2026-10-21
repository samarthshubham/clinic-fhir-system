package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static io.github.samarthshubham.clinic.support.FhirClientStub.vital;
import static io.github.samarthshubham.clinic.support.FhirDates.date;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.samarthshubham.clinic.config.ClinicalThresholds;
import io.github.samarthshubham.clinic.dto.AlertItem;
import io.github.samarthshubham.clinic.dto.LatestVitals;
import io.github.samarthshubham.clinic.dto.SnapshotResponse;
import io.github.samarthshubham.clinic.dto.VitalReading;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SnapshotServiceTest {

  private static final LocalDate MEASURED = LocalDate.of(2026, 5, 20);
  private static final String SYSTOLIC = "8480-6";
  private static final String DIASTOLIC = "8462-4";
  private static final String WEIGHT = "29463-7";
  private static final String SPO2 = "59408-5";
  private static final String HEART_RATE = "8867-4";
  private static final String TEMPERATURE = "8310-5";
  private static final Map<String, String> LOINC =
      Map.of(
          "systolic", SYSTOLIC,
          "diastolic", DIASTOLIC,
          "weight", WEIGHT,
          "spo2", SPO2,
          "heartRate", HEART_RATE,
          "temperature", TEMPERATURE);

  private FhirClientStub fhir;
  private SnapshotService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service =
        new SnapshotService(fhir.client(), new ClinicalThresholds(140, 180, 90, 120, 95.0, 90.0));
  }

  @Test
  void activeConditionsAreListedWithDisplayAndOnset() {
    Condition hypertension =
        new Condition()
            .setCode(new CodeableConcept().setText("Essential hypertension"))
            .setOnset(new DateTimeType(date(LocalDate.of(2026, 4, 2))));
    hypertension.setId("c1");
    fhir.givenSearchResult(Condition.class, bundleOf(hypertension));

    SnapshotResponse snapshot = service.getSnapshot("p1");

    assertThat(snapshot.activeConditions())
        .containsExactly(
            Map.of(
                "id", "c1",
                "display", "Essential hypertension",
                "onsetDate", "2026-04-02",
                "status", "active"));
    assertThat(fhir.lastSearchCriteria(Condition.class))
        .containsExactlyInAnyOrder(
            "subject=Patient/p1",
            "clinical-status=http://terminology.hl7.org/CodeSystem/condition-clinical|active");
  }

  @Test
  void conditionWithoutOnsetHasNullOnsetDate() {
    Condition condition = new Condition().setCode(new CodeableConcept().setText("Asthma"));
    condition.setId("c2");
    fhir.givenSearchResult(Condition.class, bundleOf(condition));

    assertThat(service.getSnapshot("p1").activeConditions())
        .singleElement()
        .satisfies(c -> assertThat(c).containsEntry("onsetDate", null));
  }

  @Test
  void activeMedicationsAreListedWithNameAndDosage() {
    MedicationStatement lisinopril =
        new MedicationStatement()
            .setMedication(new CodeableConcept().setText("Lisinopril 10 mg"))
            .addDosage(new Dosage().setText("Once daily"));
    lisinopril.setId("m1");
    MedicationStatement aspirin =
        new MedicationStatement().setMedication(new CodeableConcept().setText("Aspirin"));
    aspirin.setId("m2");
    fhir.givenSearchResult(MedicationStatement.class, bundleOf(lisinopril, aspirin));

    SnapshotResponse snapshot = service.getSnapshot("p1");

    assertThat(snapshot.currentMedications())
        .extracting(m -> m.get("id"), m -> m.get("name"), m -> m.get("dosage"))
        .containsExactly(
            tuple("m1", "Lisinopril 10 mg", "Once daily"), tuple("m2", "Aspirin", null));
    assertThat(fhir.lastSearchCriteria(MedicationStatement.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", "status=active");
  }

  @Test
  void latestVitalsUseTheNewestReadingOfEachType() {
    givenLatest(SYSTOLIC, 150);
    givenLatest(DIASTOLIC, 85);
    givenLatest(WEIGHT, 80.5);
    givenLatest(SPO2, 97);
    givenLatest(HEART_RATE, 72);
    givenLatest(TEMPERATURE, 36.8);

    LatestVitals vitals = service.getSnapshot("p1").latestVitals();

    String day = MEASURED.toString();
    assertThat(vitals.systolicBp()).isEqualTo(new VitalReading(150.0, "mmHg", day, true));
    assertThat(vitals.diastolicBp()).isEqualTo(new VitalReading(85.0, "mmHg", day, false));
    assertThat(vitals.weightKg()).isEqualTo(new VitalReading(80.5, "kg", day, false));
    assertThat(vitals.spo2Percent()).isEqualTo(new VitalReading(97.0, "%", day, false));
    assertThat(vitals.heartRate()).isEqualTo(new VitalReading(72.0, "/min", day, false));
    assertThat(vitals.temperature()).isEqualTo(new VitalReading(36.8, "Cel", day, false));
    assertThat(fhir.searches(Observation.class))
        .hasSize(6)
        .allSatisfy(c -> assertThat(c).contains("subject=Patient/p1", "_sort=-date", "_count=1"));
  }

  @Test
  void missingOrNonNumericVitalsAreNull() {
    givenLatest(SYSTOLIC, 150);
    Observation textWeight = new Observation().setValue(new StringType("heavy"));
    fhir.givenSearchResultWhen(Observation.class, bundleOf(textWeight), code(WEIGHT));

    LatestVitals vitals = service.getSnapshot("p1").latestVitals();

    assertThat(vitals.systolicBp()).isNotNull();
    assertThat(vitals.weightKg()).isNull();
    assertThat(vitals.diastolicBp()).isNull();
  }

  @Test
  void latestReadingWithoutAValueIsNull() {
    givenLatest(SYSTOLIC, 150);
    Observation noValue = vital(SPO2, 0, MEASURED).setValue(new Quantity());
    fhir.givenSearchResultWhen(Observation.class, bundleOf(noValue), code(SPO2));

    LatestVitals vitals = service.getSnapshot("p1").latestVitals();

    assertThat(vitals.spo2Percent()).isNull();
    assertThat(vitals.systolicBp()).isNotNull();
  }

  @Test
  void noVitalsMeansNoLatestVitalsAndNoAlerts() {
    SnapshotResponse snapshot = service.getSnapshot("p1");

    assertThat(snapshot.latestVitals()).isNull();
    assertThat(snapshot.alerts()).isEmpty();
  }

  @Test
  void snapshotAlertsComeFromTheLatestVitals() {
    givenLatest(SYSTOLIC, 185);
    givenLatest(SPO2, 93);

    assertThat(service.getSnapshot("p1").alerts())
        .extracting(AlertItem::severity, AlertItem::message)
        .containsExactly(
            tuple("critical", "Systolic BP 185 mmHg exceeds critical threshold of 180"),
            tuple("warning", "SpO2 93.0% below 95.0% threshold"));
  }

  @ParameterizedTest(name = "{0}={1} -> {2}")
  @CsvSource(
      delimiter = '|',
      nullValues = "none",
      value = {
        "systolic|181|critical|Systolic BP 181 mmHg exceeds critical threshold of 180",
        "systolic|180|warning|Systolic BP 180 mmHg above 140 threshold",
        "systolic|141|warning|Systolic BP 141 mmHg above 140 threshold",
        "systolic|140|none|none",
        "diastolic|121|critical|Diastolic BP 121 mmHg exceeds critical threshold of 120",
        "diastolic|120|warning|Diastolic BP 120 mmHg above 90 threshold",
        "diastolic|91|warning|Diastolic BP 91 mmHg above 90 threshold",
        "diastolic|90|none|none",
        "spo2|89.9|critical|SpO2 89.9% below critical threshold of 90.0%",
        "spo2|90|warning|SpO2 90.0% below 95.0% threshold",
        "spo2|94.9|warning|SpO2 94.9% below 95.0% threshold",
        "spo2|95|none|none",
        "heartRate|101|warning|Heart rate 101 bpm — tachycardia",
        "heartRate|100|none|none",
        "heartRate|60|none|none",
        "heartRate|59|warning|Heart rate 59 bpm — bradycardia",
        "temperature|37.9|warning|Temperature 37.9°C — fever",
        "temperature|37.8|none|none"
      })
  void alertThresholds(String vital, double value, String severity, String message) {
    List<AlertItem> alerts = service.computeAlerts(only(vital, value));

    if (severity == null) {
      assertThat(alerts).isEmpty();
    } else {
      assertThat(alerts).containsExactly(new AlertItem(severity, "vital", message, null));
    }
  }

  @ParameterizedTest(name = "{0}={1} flagged={2}")
  @CsvSource({
    "systolic,141,true",
    "systolic,140,false",
    "diastolic,91,true",
    "diastolic,90,false",
    "spo2,94.9,true",
    "spo2,95,false",
    "heartRate,101,true",
    "heartRate,59,true",
    "heartRate,80,false",
    "temperature,37.9,true",
    "temperature,37.8,false",
    "weight,250,false"
  })
  void readingsAreFlaggedOutsideTheWarningRange(String vital, double value, boolean flagged) {
    givenLatest(LOINC.get(vital), value);

    LatestVitals vitals = service.getSnapshot("p1").latestVitals();

    assertThat(reading(vitals, vital).flagged()).isEqualTo(flagged);
  }

  private void givenLatest(String loincCode, double value) {
    fhir.givenSearchResultWhen(
        Observation.class, bundleOf(vital(loincCode, value, MEASURED)), code(loincCode));
  }

  private static String code(String loincCode) {
    return "code=http://loinc.org|" + loincCode;
  }

  private static VitalReading reading(LatestVitals vitals, String vital) {
    return switch (vital) {
      case "systolic" -> vitals.systolicBp();
      case "diastolic" -> vitals.diastolicBp();
      case "weight" -> vitals.weightKg();
      case "spo2" -> vitals.spo2Percent();
      case "heartRate" -> vitals.heartRate();
      default -> vitals.temperature();
    };
  }

  private static LatestVitals only(String vital, double value) {
    VitalReading reading = new VitalReading(value, null, null, null);
    return new LatestVitals(
        vital.equals("systolic") ? reading : null,
        vital.equals("diastolic") ? reading : null,
        null,
        vital.equals("spo2") ? reading : null,
        vital.equals("heartRate") ? reading : null,
        vital.equals("temperature") ? reading : null);
  }
}
