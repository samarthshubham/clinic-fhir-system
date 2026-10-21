package io.github.shubhamforge.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.shubhamforge.clinic.config.ClinicalThresholds;
import io.github.shubhamforge.clinic.dto.AlertItem;
import io.github.shubhamforge.clinic.dto.LatestVitals;
import io.github.shubhamforge.clinic.dto.SnapshotResponse;
import io.github.shubhamforge.clinic.dto.VitalReading;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SnapshotService {

  private static final String LOINC_SYSTEM = "http://loinc.org";
  private static final String CLINICAL_STATUS_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/condition-clinical";

  private static final Map<String, String> VITAL_LOINC =
      Map.ofEntries(
          Map.entry("systolic", "8480-6"),
          Map.entry("diastolic", "8462-4"),
          Map.entry("weight", "29463-7"),
          Map.entry("spo2", "59408-5"),
          Map.entry("heartRate", "8867-4"),
          Map.entry("temperature", "8310-5"));

  private final IGenericClient fhirClient;
  private final ClinicalThresholds thresholds;

  public SnapshotService(IGenericClient fhirClient, ClinicalThresholds thresholds) {
    this.fhirClient = fhirClient;
    this.thresholds = thresholds;
  }

  public SnapshotResponse getSnapshot(String patientId) {
    List<Map<String, Object>> conditions = fetchActiveConditions(patientId);
    List<Map<String, Object>> medications = fetchActiveMedications(patientId);
    LatestVitals vitals = fetchLatestVitals(patientId);
    List<AlertItem> alerts = computeAlerts(vitals);
    return new SnapshotResponse(conditions, medications, vitals, alerts);
  }

  private List<Map<String, Object>> fetchActiveConditions(String patientId) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(Condition.class)
            .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
            .and(
                new TokenClientParam("clinical-status")
                    .exactly()
                    .systemAndCode(CLINICAL_STATUS_SYSTEM, "active"))
            .returnBundle(Bundle.class)
            .execute();
    List<Map<String, Object>> result = new ArrayList<>();
    if (bundle.getEntry() == null) return result;
    for (var entry : bundle.getEntry()) {
      Condition c = (Condition) entry.getResource();
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", c.getIdElement().getIdPart());
      m.put("display", c.getCode() != null ? c.getCode().getText() : null);
      m.put(
          "onsetDate",
          c.hasOnsetDateTimeType()
              ? c.getOnsetDateTimeType()
                  .getValue()
                  .toInstant()
                  .atZone(ZoneId.systemDefault())
                  .toLocalDate()
                  .toString()
              : null);
      m.put("status", "active");
      result.add(m);
    }
    return result;
  }

  private List<Map<String, Object>> fetchActiveMedications(String patientId) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(MedicationStatement.class)
            .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
            .and(new TokenClientParam("status").exactly().code("active"))
            .returnBundle(Bundle.class)
            .execute();
    List<Map<String, Object>> result = new ArrayList<>();
    if (bundle.getEntry() == null) return result;
    for (var entry : bundle.getEntry()) {
      MedicationStatement ms = (MedicationStatement) entry.getResource();
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", ms.getIdElement().getIdPart());
      if (ms.getMedication() instanceof CodeableConcept cc) {
        m.put("name", cc.getText());
      }
      m.put(
          "dosage",
          ms.hasDosage() && !ms.getDosage().isEmpty() ? ms.getDosageFirstRep().getText() : null);
      m.put("status", "active");
      result.add(m);
    }
    return result;
  }

  private LatestVitals fetchLatestVitals(String patientId) {
    VitalReading systolic = fetchLatestVital(patientId, "systolic", "mmHg");
    VitalReading diastolic = fetchLatestVital(patientId, "diastolic", "mmHg");
    VitalReading weight = fetchLatestVital(patientId, "weight", "kg");
    VitalReading spo2 = fetchLatestVital(patientId, "spo2", "%");
    VitalReading heartRate = fetchLatestVital(patientId, "heartRate", "/min");
    VitalReading temperature = fetchLatestVital(patientId, "temperature", "Cel");
    if (systolic == null
        && diastolic == null
        && weight == null
        && spo2 == null
        && heartRate == null
        && temperature == null) return null;
    return new LatestVitals(systolic, diastolic, weight, spo2, heartRate, temperature);
  }

  private VitalReading fetchLatestVital(String patientId, String type, String unit) {
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
            .count(1)
            .returnBundle(Bundle.class)
            .execute();
    if (bundle.getEntry() == null || bundle.getEntry().isEmpty()) return null;
    Observation obs = (Observation) bundle.getEntryFirstRep().getResource();
    if (!(obs.getValue() instanceof Quantity q) || !q.hasValue()) return null;
    double value = q.getValue().doubleValue();
    String date =
        obs.hasEffectiveDateTimeType()
            ? obs.getEffectiveDateTimeType()
                .getValue()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
            : null;
    boolean flagged = isFlagged(type, value);
    return new VitalReading(value, unit, date, flagged);
  }

  private boolean isFlagged(String type, double value) {
    return switch (type) {
      case "systolic" -> value > thresholds.systolicWarn();
      case "diastolic" -> value > thresholds.diastolicWarn();
      case "spo2" -> value < thresholds.spo2Warn();
      case "heartRate" -> value > 100 || value < 60;
      case "temperature" -> value > 37.8;
      default -> false;
    };
  }

  List<AlertItem> computeAlerts(LatestVitals vitals) {
    if (vitals == null) return List.of();
    List<AlertItem> alerts = new ArrayList<>();
    if (vitals.systolicBp() != null && vitals.systolicBp().value() != null) {
      double v = vitals.systolicBp().value();
      if (v > thresholds.systolicCritical()) {
        alerts.add(
            alert(
                "critical",
                "vital",
                "Systolic BP "
                    + (int) v
                    + " mmHg exceeds critical threshold of "
                    + thresholds.systolicCritical(),
                null));
      } else if (v > thresholds.systolicWarn()) {
        alerts.add(
            alert(
                "warning",
                "vital",
                "Systolic BP "
                    + (int) v
                    + " mmHg above "
                    + thresholds.systolicWarn()
                    + " threshold",
                null));
      }
    }
    if (vitals.diastolicBp() != null && vitals.diastolicBp().value() != null) {
      double v = vitals.diastolicBp().value();
      if (v > thresholds.diastolicCritical()) {
        alerts.add(
            alert(
                "critical",
                "vital",
                "Diastolic BP "
                    + (int) v
                    + " mmHg exceeds critical threshold of "
                    + thresholds.diastolicCritical(),
                null));
      } else if (v > thresholds.diastolicWarn()) {
        alerts.add(
            alert(
                "warning",
                "vital",
                "Diastolic BP "
                    + (int) v
                    + " mmHg above "
                    + thresholds.diastolicWarn()
                    + " threshold",
                null));
      }
    }
    if (vitals.spo2Percent() != null && vitals.spo2Percent().value() != null) {
      double v = vitals.spo2Percent().value();
      if (v < thresholds.spo2Critical()) {
        alerts.add(
            alert(
                "critical",
                "vital",
                "SpO2 " + v + "% below critical threshold of " + thresholds.spo2Critical() + "%",
                null));
      } else if (v < thresholds.spo2Warn()) {
        alerts.add(
            alert(
                "warning",
                "vital",
                "SpO2 " + v + "% below " + thresholds.spo2Warn() + "% threshold",
                null));
      }
    }
    if (vitals.heartRate() != null && vitals.heartRate().value() != null) {
      double v = vitals.heartRate().value();
      if (v > 100) {
        alerts.add(alert("warning", "vital", "Heart rate " + (int) v + " bpm — tachycardia", null));
      } else if (v < 60) {
        alerts.add(alert("warning", "vital", "Heart rate " + (int) v + " bpm — bradycardia", null));
      }
    }
    if (vitals.temperature() != null && vitals.temperature().value() != null) {
      double v = vitals.temperature().value();
      if (v > 37.8) {
        alerts.add(alert("warning", "vital", "Temperature " + v + "°C — fever", null));
      }
    }
    return alerts;
  }

  private AlertItem alert(String severity, String type, String message, String resourceId) {
    return new AlertItem(severity, type, message, resourceId);
  }
}
