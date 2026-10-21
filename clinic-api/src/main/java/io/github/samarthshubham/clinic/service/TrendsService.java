package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.DateClientParam;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.config.ClinicalThresholds;
import io.github.samarthshubham.clinic.dto.BpSeries;
import io.github.samarthshubham.clinic.dto.DataPoint;
import io.github.samarthshubham.clinic.dto.SimpleSeries;
import io.github.samarthshubham.clinic.dto.TrendsResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.springframework.stereotype.Service;

@Service
public class TrendsService {

  private static final String LOINC_SYSTEM = "http://loinc.org";
  private static final int MAX_POINTS = 30;

  private static final Map<String, String> VITAL_LOINC =
      Map.of(
          "systolic", "8480-6",
          "diastolic", "8462-4",
          "weight", "29463-7",
          "spo2", "59408-5");

  private final IGenericClient fhirClient;
  private final ClinicalThresholds thresholds;

  public TrendsService(IGenericClient fhirClient, ClinicalThresholds thresholds) {
    this.fhirClient = fhirClient;
    this.thresholds = thresholds;
  }

  public TrendsResponse getTrends(String patientId, String typesCsv, String period) {
    String resolvedPeriod = period != null ? period : "30d";
    LocalDate to = LocalDate.now();
    LocalDate from = resolvePeriodStart(to, resolvedPeriod);

    Map<String, Object> series = new LinkedHashMap<>();
    String[] types = typesCsv != null ? typesCsv.split(",") : new String[] {"bp"};

    for (String type : types) {
      String t = type.trim().toLowerCase();
      if ("bp".equals(t)) {
        series.put("bp", buildBpSeries(patientId, from));
      } else if (VITAL_LOINC.containsKey(t)) {
        series.put(t, buildSimpleSeries(patientId, t, from));
      }
    }
    return new TrendsResponse(resolvedPeriod, from.toString(), to.toString(), series);
  }

  private BpSeries buildBpSeries(String patientId, LocalDate from) {
    List<DataPoint> systolic = fetchSeries(patientId, "systolic", from);
    List<DataPoint> diastolic = fetchSeries(patientId, "diastolic", from);
    Map<String, Integer> ref =
        Map.of(
            "systolicMax", thresholds.systolicWarn(),
            "diastolicMax", thresholds.diastolicWarn());
    return new BpSeries(downsample(systolic), downsample(diastolic), ref);
  }

  private SimpleSeries buildSimpleSeries(String patientId, String type, LocalDate from) {
    List<DataPoint> points = downsample(fetchSeries(patientId, type, from));
    String unit = "weight".equals(type) ? "kg" : "%";
    Map<String, Object> ref = new LinkedHashMap<>();
    if ("spo2".equals(type)) ref.put("min", thresholds.spo2Warn());
    return new SimpleSeries(points, unit, ref.isEmpty() ? null : ref);
  }

  private List<DataPoint> fetchSeries(String patientId, String type, LocalDate from) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(Observation.class)
            .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
            .and(
                new TokenClientParam("code")
                    .exactly()
                    .systemAndCode(LOINC_SYSTEM, VITAL_LOINC.get(type)))
            .and(new DateClientParam("date").afterOrEquals().day(from.toString()))
            .sort()
            .ascending("date")
            .returnBundle(Bundle.class)
            .execute();

    List<DataPoint> points = new ArrayList<>();
    if (bundle.getEntry() == null) return points;
    for (var entry : bundle.getEntry()) {
      Observation obs = (Observation) entry.getResource();
      if (!(obs.getValue() instanceof Quantity q) || !q.hasValue()) continue;
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
      if (date != null) points.add(new DataPoint(date, value));
    }
    return points;
  }

  private List<DataPoint> downsample(List<DataPoint> points) {
    if (points.size() <= MAX_POINTS) return points;
    int n = (int) Math.ceil((double) points.size() / MAX_POINTS);
    List<DataPoint> sampled = new ArrayList<>();
    for (int i = 0; i < points.size(); i += n) {
      sampled.add(points.get(i));
    }
    return sampled;
  }

  private LocalDate resolvePeriodStart(LocalDate to, String period) {
    return switch (period) {
      case "7d" -> to.minusDays(7);
      case "90d" -> to.minusDays(90);
      case "1y" -> to.minusYears(1);
      default -> to.minusDays(30);
    };
  }
}
