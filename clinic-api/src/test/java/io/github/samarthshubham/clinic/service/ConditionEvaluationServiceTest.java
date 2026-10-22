package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static io.github.samarthshubham.clinic.support.FhirClientStub.quantityObservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.config.ClinicalThresholds;
import io.github.samarthshubham.clinic.dto.ConditionRequest;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConditionEvaluationServiceTest {

  private static final String PATIENT_ID = "p1";
  private static final String HYPERTENSION = "59621000";
  private static final String HYPOXEMIA = "86290005";

  @Mock private ConditionService conditionService;

  private FhirClientStub fhir;
  private ConditionEvaluationService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    ClinicalThresholds thresholds = new ClinicalThresholds(140, 180, 90, 120, 95.0, 90.0);
    service = new ConditionEvaluationService(fhir.client(), thresholds, conditionService);
  }

  @Test
  void threeSystolicReadingsAboveWarnCreateHypertension() {
    givenReadings(150, 145, 141);
    LocalDate before = LocalDate.now();

    service.evaluate(PATIENT_ID, "systolic", 150);

    ConditionRequest request = capturedCondition();
    assertThat(request.patientId()).isEqualTo(PATIENT_ID);
    assertThat(request.encounterId()).isNull();
    assertThat(request.code()).isEqualTo(HYPERTENSION);
    assertThat(request.display()).isEqualTo("Essential hypertension");
    assertThat(request.clinicalStatus()).isEqualTo("active");
    assertThat(request.onsetDate()).isBetween(before, LocalDate.now());
  }

  @Test
  void hypertensionSearchAsksForThisPatientsLatestSystolicReadings() {
    givenReadings(150, 145, 141);

    service.evaluate(PATIENT_ID, "systolic", 150);

    assertThat(fhir.lastSearchCriteria(Observation.class))
        .containsExactly(
            "subject=Patient/" + PATIENT_ID,
            "code=http://loinc.org|8480-6",
            "_sort=-date",
            "_count=20");
  }

  @Test
  void systolicReadingAtWarnThresholdDoesNotCount() {
    givenReadings(150, 150, 140);

    service.evaluate(PATIENT_ID, "systolic", 150);

    verifyNoConditionCreated();
  }

  @Test
  void fewerThanThreeSystolicReadingsDoNotCreateHypertension() {
    givenReadings(160, 170);

    service.evaluate(PATIENT_ID, "systolic", 160);

    verifyNoInteractions(conditionService);
  }

  @Test
  void normalSystolicReadingAmongLastThreeDoesNotCreateHypertension() {
    givenReadings(160, 120, 170);

    service.evaluate(PATIENT_ID, "systolic", 160);

    verifyNoConditionCreated();
  }

  @Test
  void activeHypertensionIsNotDuplicated() {
    givenReadings(150, 145, 141);
    when(conditionService.hasActiveConditionWithCode(PATIENT_ID, HYPERTENSION)).thenReturn(true);

    service.evaluate(PATIENT_ID, "systolic", 150);

    verifyNoConditionCreated();
  }

  @Test
  void nonQuantityReadingsAreSkippedRatherThanCountedAsNormal() {
    Observation textReading = new Observation().setValue(new StringType("high"));
    fhir.givenSearchResult(
        Observation.class,
        bundleOf(
            textReading,
            quantityObservation(150),
            quantityObservation(160),
            quantityObservation(170)));

    service.evaluate(PATIENT_ID, "systolic", 150);

    assertThat(capturedCondition().code()).isEqualTo(HYPERTENSION);
  }

  @Test
  void nonQuantityReadingsDoNotCountTowardsTheMinimum() {
    Observation textReading = new Observation().setValue(new StringType("high"));
    fhir.givenSearchResult(
        Observation.class,
        bundleOf(quantityObservation(150), textReading, quantityObservation(160)));

    service.evaluate(PATIENT_ID, "systolic", 150);

    verifyNoInteractions(conditionService);
  }

  @Test
  void readingsWithoutAValueAreIgnored() {
    Observation noValue = new Observation().setValue(new Quantity());
    fhir.givenSearchResult(
        Observation.class, bundleOf(quantityObservation(150), noValue, quantityObservation(160)));

    service.evaluate(PATIENT_ID, "systolic", 150);

    verifyNoInteractions(conditionService);
  }

  @Test
  void emptySearchResultDoesNotCreateHypertension() {
    fhir.givenSearchResult(Observation.class, new Bundle());

    service.evaluate(PATIENT_ID, "systolic", 150);

    verifyNoInteractions(conditionService);
  }

  @Test
  void twoSpo2ReadingsBelowWarnCreateHypoxemia() {
    givenReadings(93, 94);

    service.evaluate(PATIENT_ID, "spo2", 93);

    ConditionRequest request = capturedCondition();
    assertThat(request.code()).isEqualTo(HYPOXEMIA);
    assertThat(request.display()).isEqualTo("Hypoxemia");
    assertThat(request.clinicalStatus()).isEqualTo("active");
    assertThat(fhir.lastSearchCriteria(Observation.class))
        .containsExactly(
            "subject=Patient/" + PATIENT_ID,
            "code=http://loinc.org|59408-5",
            "_sort=-date",
            "_count=20");
  }

  @Test
  void spo2ReadingAtWarnThresholdDoesNotCount() {
    givenReadings(93, 95.0);

    service.evaluate(PATIENT_ID, "spo2", 93);

    verifyNoConditionCreated();
  }

  @Test
  void singleLowSpo2ReadingDoesNotCreateHypoxemia() {
    givenReadings(88);

    service.evaluate(PATIENT_ID, "spo2", 88);

    verifyNoInteractions(conditionService);
  }

  @Test
  void activeHypoxemiaIsNotDuplicated() {
    givenReadings(93, 94);
    when(conditionService.hasActiveConditionWithCode(PATIENT_ID, HYPOXEMIA)).thenReturn(true);

    service.evaluate(PATIENT_ID, "spo2", 93);

    verifyNoConditionCreated();
  }

  @Test
  void otherVitalTypesAreNotEvaluated() {
    service.evaluate(PATIENT_ID, "weight", 120);
    service.evaluate(PATIENT_ID, "diastolic", 130);

    verifyNoInteractions(conditionService);
    verify(fhir.client(), never()).search();
  }

  @Test
  void patientReportedReadingsDoNotCountTowardsHypertension() {
    Observation homeReading = quantityObservation(190);
    homeReading.addPerformer(new Reference("Patient/" + PATIENT_ID));
    Observation secondHomeReading = quantityObservation(185);
    secondHomeReading.addPerformer(new Reference("Patient/" + PATIENT_ID));
    fhir.givenSearchResult(
        Observation.class,
        bundleOf(
            homeReading, secondHomeReading, quantityObservation(150), quantityObservation(120)));

    service.evaluate(PATIENT_ID, "systolic", 150);

    verifyNoConditionCreated();
  }

  private void givenReadings(double... values) {
    Observation[] observations = new Observation[values.length];
    for (int i = 0; i < values.length; i++) {
      observations[i] = quantityObservation(values[i]);
    }
    fhir.givenSearchResult(Observation.class, bundleOf(observations));
  }

  private ConditionRequest capturedCondition() {
    ArgumentCaptor<ConditionRequest> captor = ArgumentCaptor.forClass(ConditionRequest.class);
    verify(conditionService).createCondition(captor.capture());
    return captor.getValue();
  }

  private void verifyNoConditionCreated() {
    verify(conditionService, never()).createCondition(any());
  }
}
