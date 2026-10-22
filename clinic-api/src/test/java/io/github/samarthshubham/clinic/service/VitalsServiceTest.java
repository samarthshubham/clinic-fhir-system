package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static io.github.samarthshubham.clinic.support.FhirClientStub.quantityObservation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.github.samarthshubham.clinic.dto.ObservationResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.dto.VitalsRequest;
import io.github.samarthshubham.clinic.exception.ReferenceValidationException;
import io.github.samarthshubham.clinic.mapper.EncounterMapper;
import io.github.samarthshubham.clinic.mapper.ObservationMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    service =
        new VitalsService(
            fhir.client(),
            new ObservationMapper(),
            conditionEvaluationService,
            new EncounterService(fhir.client(), new EncounterMapper()));
    Encounter visit = new Encounter();
    visit.setSubject(new Reference("Patient/" + PATIENT_ID));
    fhir.givenStored(visit, "e1");
  }

  @Test
  void recordingFullPanelCreatesOneObservationPerVital() {
    List<ObservationResponse> result = service.recordVitals(fullPanel());

    assertThat(result)
        .extracting(ObservationResponse::id, ObservationResponse::type)
        .containsExactly(
            tuple("1", "systolic"),
            tuple("2", "diastolic"),
            tuple("3", "weight"),
            tuple("4", "spo2"),
            tuple("5", "heartRate"),
            tuple("6", "temperature"));
    assertThat(fhir.created())
        .map(Observation.class::cast)
        .extracting(
            obs -> obs.getCode().getCodingFirstRep().getCode(),
            obs -> obs.getValueQuantity().getValue().doubleValue(),
            obs -> obs.getValueQuantity().getUnit())
        .containsExactly(
            tuple("8480-6", 150.0, "mm[Hg]"),
            tuple("8462-4", 95.0, "mm[Hg]"),
            tuple("29463-7", 80.5, "kg"),
            tuple("59408-5", 93.0, "%"),
            tuple("8867-4", 72.0, "/min"),
            tuple("8310-5", 37.1, "Cel"));
  }

  @Test
  void recordingFullPanelEvaluatesOnlyTrackedVitals() {
    service.recordVitals(fullPanel());

    verify(conditionEvaluationService).evaluate(PATIENT_ID, "systolic", 150.0);
    verify(conditionEvaluationService).evaluate(PATIENT_ID, "diastolic", 95.0);
    verify(conditionEvaluationService).evaluate(PATIENT_ID, "weight", 80.5);
    verify(conditionEvaluationService).evaluate(PATIENT_ID, "spo2", 93.0);
    verifyNoMoreInteractions(conditionEvaluationService);
  }

  @Test
  void recordingSingleVitalCreatesOneObservation() {
    VitalsRequest request =
        new VitalsRequest(PATIENT_ID, "e1", TODAY, 150, null, null, null, null, null);

    List<ObservationResponse> result = service.recordVitals(request);

    assertThat(result)
        .singleElement()
        .extracting(ObservationResponse::patientId, ObservationResponse::encounterId)
        .containsExactly(PATIENT_ID, "e1");
    Observation created = (Observation) fhir.created().get(0);
    assertThat(created.getSubject().getReference()).isEqualTo("Patient/" + PATIENT_ID);
    assertThat(created.getEncounter().getReference()).isEqualTo("Encounter/e1");
    verify(conditionEvaluationService).evaluate(PATIENT_ID, "systolic", 150.0);
  }

  @Test
  void recordingWithoutVitalsCreatesNothing() {
    VitalsRequest request =
        new VitalsRequest(PATIENT_ID, null, TODAY, null, null, null, null, null, null);

    assertThat(service.recordVitals(request)).isEmpty();
    assertThat(fhir.created()).isEmpty();
    verifyNoInteractions(conditionEvaluationService);
  }

  @Test
  void failedEvaluationDoesNotStopRecordingOrLaterEvaluations() {
    doThrow(new RuntimeException("FHIR server down"))
        .doNothing()
        .when(conditionEvaluationService)
        .evaluate(anyString(), anyString(), anyDouble());

    assertThat(service.recordVitals(fullPanel())).hasSize(6);
    verify(conditionEvaluationService, times(4)).evaluate(anyString(), anyString(), anyDouble());
  }

  @Test
  void searchWithoutTypeReturnsAllVitalsForThePatient() {
    fhir.givenSearchResult(
        Observation.class, bundleOf(quantityObservation(150), quantityObservation(80)));

    PageResponse<ObservationResponse> result = service.getVitals(PATIENT_ID, null);

    assertThat(result.items()).extracting(ObservationResponse::value).containsExactly(150.0, 80.0);
    assertThat(result.total()).isEqualTo(2);
    assertThat(fhir.lastSearchCriteria(Observation.class))
        .containsExactly("subject=Patient/" + PATIENT_ID);
  }

  @ParameterizedTest
  @CsvSource({"systolic,8480-6", "diastolic,8462-4", "weight,29463-7", "spo2,59408-5"})
  void searchWithKnownTypeFiltersByItsLoincCode(String type, String loincCode) {
    fhir.givenSearchResult(Observation.class, bundleOf(quantityObservation(80)));

    assertThat(service.getVitals(PATIENT_ID, type).items()).hasSize(1);
    assertThat(fhir.lastSearchCriteria(Observation.class))
        .containsExactly("subject=Patient/" + PATIENT_ID, "code=http://loinc.org|" + loincCode);
  }

  @Test
  void searchWithUnknownTypeIgnoresFilter() {
    service.getVitals(PATIENT_ID, "bmi");

    assertThat(fhir.lastSearchCriteria(Observation.class))
        .containsExactly("subject=Patient/" + PATIENT_ID);
  }

  private static VitalsRequest fullPanel() {
    return new VitalsRequest(PATIENT_ID, null, TODAY, 150, 95, 80.5, 93.0, 72, 37.1);
  }

  @Test
  void rejectsAnEncounterOfAnotherPatient() {
    Encounter otherVisit = new Encounter();
    otherVisit.setSubject(new Reference("Patient/someone-else"));
    fhir.givenStored(otherVisit, "e9");

    assertThatThrownBy(
            () ->
                service.recordVitals(
                    new VitalsRequest(PATIENT_ID, "e9", TODAY, 150, null, null, null, null, null)))
        .isInstanceOf(ReferenceValidationException.class)
        .hasMessage(
            "Referenced Encounter with id 'e9' does not belong to Patient '" + PATIENT_ID + "'");
    assertThat(fhir.created()).isEmpty();
  }

  @Test
  void rejectsAnUnknownEncounter() {
    assertThatThrownBy(
            () ->
                service.recordVitals(
                    new VitalsRequest(
                        PATIENT_ID, "nope", TODAY, 150, null, null, null, null, null)))
        .isInstanceOf(ReferenceValidationException.class)
        .hasMessage("Referenced Encounter with id 'nope' does not exist");
  }

  @Test
  void patientReportedVitalsAreMarkedAndSkipConditionEvaluation() {
    List<ObservationResponse> result =
        service.recordVitals(
            new VitalsRequest(PATIENT_ID, null, TODAY, 185, null, null, null, null, null), true);

    assertThat(result).hasSize(1);
    assertThat(fhir.created())
        .singleElement()
        .isInstanceOfSatisfying(
            Observation.class,
            obs ->
                assertThat(obs.getPerformerFirstRep().getReference())
                    .isEqualTo("Patient/" + PATIENT_ID));
    verifyNoInteractions(conditionEvaluationService);
  }
}
