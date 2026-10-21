package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static io.github.shubhamforge.clinic.support.FhirClientStub.quantityObservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.shubhamforge.clinic.dto.VitalsRequest;
import io.github.shubhamforge.clinic.mapper.ObservationMapper;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VitalsServiceTest {

  private static final String PATIENT_ID = "p1";
  private static final LocalDate TODAY = LocalDate.now();

  @Mock private ConditionEvaluationService conditionEvaluationService;

  private FhirClientStub fhir;
  private VitalsService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new VitalsService(fhir.client(), new ObservationMapper(), conditionEvaluationService);
  }

  @Test
  void recordingFullPanelCreatesOneObservationPerVital() {
    Bundle result = service.recordVitals(fullPanel());

    assertThat(fhir.created()).hasSize(6);
    assertThat(result.getType()).isEqualTo(Bundle.BundleType.COLLECTION);
    assertThat(result.getEntry())
        .extracting(entry -> entry.getResource().getIdElement().getIdPart())
        .containsExactly("1", "2", "3", "4", "5", "6");
  }

  @Test
  void recordingFullPanelEvaluatesOnlyTrackedVitals() {
    service.recordVitals(fullPanel());

    verify(conditionEvaluationService).evaluate(PATIENT_ID, "systolic", 150.0);
    verify(conditionEvaluationService).evaluate(PATIENT_ID, "diastolic", 95.0);
    verify(conditionEvaluationService).evaluate(PATIENT_ID, "weight", 80.5);
    verify(conditionEvaluationService).evaluate(PATIENT_ID, "spo2", 93.0);
    verify(conditionEvaluationService, never())
        .evaluate(eq(PATIENT_ID), eq("heartRate"), anyDouble());
    verify(conditionEvaluationService, never())
        .evaluate(eq(PATIENT_ID), eq("temperature"), anyDouble());
  }

  @Test
  void recordingSingleVitalCreatesOneObservation() {
    VitalsRequest request =
        new VitalsRequest(PATIENT_ID, "e1", TODAY, 150, null, null, null, null, null);

    Bundle result = service.recordVitals(request);

    assertThat(result.getEntry()).hasSize(1);
    Observation created = (Observation) fhir.created().get(0);
    assertThat(created.getSubject().getReference()).isEqualTo("Patient/" + PATIENT_ID);
    assertThat(created.getEncounter().getReference()).isEqualTo("Encounter/e1");
    verify(conditionEvaluationService).evaluate(PATIENT_ID, "systolic", 150.0);
  }

  @Test
  void recordingWithoutVitalsCreatesNothing() {
    VitalsRequest request =
        new VitalsRequest(PATIENT_ID, null, TODAY, null, null, null, null, null, null);

    Bundle result = service.recordVitals(request);

    assertThat(result.getEntry()).isEmpty();
    assertThat(fhir.created()).isEmpty();
    verifyNoInteractions(conditionEvaluationService);
  }

  @Test
  void failedEvaluationDoesNotFailRecording() {
    doThrow(new RuntimeException("FHIR server down"))
        .when(conditionEvaluationService)
        .evaluate(anyString(), anyString(), anyDouble());

    Bundle result = service.recordVitals(fullPanel());

    assertThat(result.getEntry()).hasSize(6);
  }

  @Test
  void searchWithoutTypeReturnsAllVitals() {
    Bundle vitals = bundleOf(quantityObservation(150), quantityObservation(80));
    fhir.givenSearchResult(Observation.class, vitals);

    Bundle result = service.getVitals(PATIENT_ID, null);

    assertThat(result).isSameAs(vitals);
    verify(fhir.query(Observation.class), never()).and(any());
  }

  @Test
  void searchWithKnownTypeFiltersByCode() {
    Bundle vitals = bundleOf(quantityObservation(80));
    fhir.givenSearchResult(Observation.class, vitals);

    Bundle result = service.getVitals(PATIENT_ID, "weight");

    assertThat(result).isSameAs(vitals);
    verify(fhir.query(Observation.class)).and(any());
  }

  @Test
  void searchWithUnknownTypeIgnoresFilter() {
    service.getVitals(PATIENT_ID, "bmi");

    verify(fhir.query(Observation.class), never()).and(any());
  }

  private static VitalsRequest fullPanel() {
    return new VitalsRequest(PATIENT_ID, null, TODAY, 150, 95, 80.5, 93.0, 72, 37.1);
  }
}
