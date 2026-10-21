package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.config.ClinicalThresholds;
import io.github.samarthshubham.clinic.dto.ConditionRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConditionEvaluationService {

  // SNOMED codes for auto-detected conditions
  private static final String HYPERTENSION_CODE = "59621000";
  private static final String LOW_SPO2_CODE = "86290005";
  private static final String LOINC_SYSTEM = "http://loinc.org";

  private static final Map<String, String> VITAL_LOINC =
      Map.of(
          "systolic", "8480-6",
          "spo2", "59408-5");

  private final IGenericClient fhirClient;
  private final ClinicalThresholds thresholds;
  private final ConditionService conditionService;

  public ConditionEvaluationService(
      IGenericClient fhirClient, ClinicalThresholds thresholds, ConditionService conditionService) {
    this.fhirClient = fhirClient;
    this.thresholds = thresholds;
    this.conditionService = conditionService;
  }

  public void evaluate(String patientId, String vitalType, double value) {
    if ("systolic".equals(vitalType)) {
      evaluateHypertension(patientId);
    } else if ("spo2".equals(vitalType)) {
      evaluateLowSpo2(patientId);
    }
  }

  private void evaluateHypertension(String patientId) {
    List<Double> last3 = fetchLastNValues(patientId, "systolic", 3);
    if (last3.size() < 3) return;
    boolean allElevated = last3.stream().allMatch(v -> v > thresholds.systolicWarn());
    if (!allElevated) return;
    if (conditionService.hasActiveConditionWithCode(patientId, HYPERTENSION_CODE)) return;
    log.info("Auto-detecting hypertension for patient {}", patientId);
    conditionService.createCondition(
        new ConditionRequest(
            patientId,
            null,
            HYPERTENSION_CODE,
            "Essential hypertension",
            "active",
            LocalDate.now()));
  }

  private void evaluateLowSpo2(String patientId) {
    List<Double> last2 = fetchLastNValues(patientId, "spo2", 2);
    if (last2.size() < 2) return;
    boolean allLow = last2.stream().allMatch(v -> v < thresholds.spo2Warn());
    if (!allLow) return;
    if (conditionService.hasActiveConditionWithCode(patientId, LOW_SPO2_CODE)) return;
    log.info("Auto-detecting low SpO2 for patient {}", patientId);
    conditionService.createCondition(
        new ConditionRequest(
            patientId, null, LOW_SPO2_CODE, "Hypoxemia", "active", LocalDate.now()));
  }

  private List<Double> fetchLastNValues(String patientId, String type, int n) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(Observation.class)
            .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
            .and(
                new TokenClientParam("code")
                    .exactly()
                    .systemAndCode(LOINC_SYSTEM, VITAL_LOINC.get(type)))
            .sort()
            .descending("date")
            .count(n)
            .returnBundle(Bundle.class)
            .execute();
    if (bundle.getEntry() == null) return List.of();
    return bundle.getEntry().stream()
        .map(e -> (Observation) e.getResource())
        .filter(obs -> obs.getValue() instanceof Quantity q && q.hasValue())
        .map(obs -> ((Quantity) obs.getValue()).getValue().doubleValue())
        .toList();
  }
}
