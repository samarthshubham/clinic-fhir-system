package io.github.shubhamforge.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.shubhamforge.clinic.dto.VitalsRequest;
import io.github.shubhamforge.clinic.mapper.ObservationMapper;
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

  public VitalsService(
      IGenericClient fhirClient,
      ObservationMapper observationMapper,
      ConditionEvaluationService conditionEvaluationService) {
    this.fhirClient = fhirClient;
    this.observationMapper = observationMapper;
    this.conditionEvaluationService = conditionEvaluationService;
  }

  public Bundle recordVitals(VitalsRequest request) {
    Bundle result = new Bundle();
    result.setType(Bundle.BundleType.COLLECTION);
    observationMapper
        .toFhirObservations(request)
        .forEach(
            obs -> {
              var outcome = fhirClient.create().resource(obs).execute();
              String id = outcome.getId().getIdPart();
              Observation created =
                  fhirClient.read().resource(Observation.class).withId(id).execute();
              result.addEntry().setResource(created);
              triggerConditionEvaluation(request.patientId(), created);
            });
    return result;
  }

  public Bundle getVitals(String patientId, String type) {
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
    return query.execute();
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
