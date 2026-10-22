package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.dto.EncounterResponse;
import io.github.samarthshubham.clinic.dto.ObservationResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.dto.VitalsRequest;
import io.github.samarthshubham.clinic.exception.ReferenceValidationException;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.ObservationMapper;
import io.github.samarthshubham.clinic.mapper.PageMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VitalsService {

  private static final String LOINC_SYSTEM = "http://loinc.org";

  private static final Map<String, String> TYPE_TO_LOINC =
      Map.of(
          "systolic", "8480-6",
          "diastolic", "8462-4",
          "weight", "29463-7",
          "spo2", "59408-5");

  private final IGenericClient fhirClient;
  private final ObservationMapper observationMapper;
  private final ConditionEvaluationService conditionEvaluationService;
  private final EncounterService encounterService;

  public VitalsService(
      IGenericClient fhirClient,
      ObservationMapper observationMapper,
      ConditionEvaluationService conditionEvaluationService,
      EncounterService encounterService) {
    this.fhirClient = fhirClient;
    this.observationMapper = observationMapper;
    this.conditionEvaluationService = conditionEvaluationService;
    this.encounterService = encounterService;
  }

  public List<ObservationResponse> recordVitals(VitalsRequest request) {
    return recordVitals(request, false);
  }

  public List<ObservationResponse> recordVitals(VitalsRequest request, boolean patientReported) {
    requireEncounterOfSamePatient(request);
    return observationMapper.toFhirObservations(request, patientReported).stream()
        .map(
            obs -> {
              var outcome = fhirClient.create().resource(obs).execute();
              String id = outcome.getId().getIdPart();
              Observation created =
                  fhirClient.read().resource(Observation.class).withId(id).execute();
              if (!patientReported) {
                triggerConditionEvaluation(request.patientId(), created);
              }
              return observationMapper.toResponse(created);
            })
        .toList();
  }

  private void requireEncounterOfSamePatient(VitalsRequest request) {
    if (request.encounterId() == null) {
      return;
    }
    EncounterResponse encounter;
    try {
      encounter = encounterService.getEncounter(request.encounterId());
    } catch (ResourceNotFoundException e) {
      throw new ReferenceValidationException("Encounter", request.encounterId());
    }
    if (!request.patientId().equals(encounter.patientId())) {
      throw new ReferenceValidationException(
          "Encounter", request.encounterId(), request.patientId());
    }
  }

  public PageResponse<ObservationResponse> getVitals(String patientId, String type) {
    var query =
        fhirClient
            .search()
            .forResource(Observation.class)
            .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
            .returnBundle(Bundle.class);
    if (type != null && TYPE_TO_LOINC.containsKey(type)) {
      query =
          query.and(
              new TokenClientParam("code")
                  .exactly()
                  .systemAndCode(LOINC_SYSTEM, TYPE_TO_LOINC.get(type)));
    }
    return PageMapper.toPage(query.execute(), Observation.class, observationMapper::toResponse);
  }

  private void triggerConditionEvaluation(String patientId, Observation obs) {
    if (!(obs.getValue() instanceof Quantity q) || !q.hasValue()) return;
    double value = q.getValue().doubleValue();
    String vitalType = resolveVitalType(obs);
    if (vitalType == null) return;
    try {
      conditionEvaluationService.evaluate(patientId, vitalType, value);
    } catch (Exception e) {
      log.warn(
          "Condition evaluation failed for patient {} vital {} — {}",
          patientId,
          vitalType,
          e.getMessage());
    }
  }

  private String resolveVitalType(Observation obs) {
    if (!obs.hasCode() || !obs.getCode().hasCoding()) return null;
    String loincCode = obs.getCode().getCodingFirstRep().getCode();
    return TYPE_TO_LOINC.entrySet().stream()
        .filter(e -> e.getValue().equals(loincCode))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }
}
