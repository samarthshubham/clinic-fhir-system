package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.ObservationResponse;
import io.github.samarthshubham.clinic.dto.VitalsRequest;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
public class ObservationMapper {

  private static final String LOINC_SYSTEM = "http://loinc.org";
  private static final String UCUM_SYSTEM = "http://unitsofmeasure.org";

  // [loincCode, display, unit]
  private static final Map<String, String[]> VITAL_META =
      Map.ofEntries(
          Map.entry("systolic", new String[] {"8480-6", "Systolic blood pressure", "mm[Hg]"}),
          Map.entry("diastolic", new String[] {"8462-4", "Diastolic blood pressure", "mm[Hg]"}),
          Map.entry("weight", new String[] {"29463-7", "Body weight", "kg"}),
          Map.entry("spo2", new String[] {"59408-5", "Oxygen saturation", "%"}),
          Map.entry("heartRate", new String[] {"8867-4", "Heart rate", "/min"}),
          Map.entry("temperature", new String[] {"8310-5", "Body temperature", "Cel"}));

  public List<Observation> toFhirObservations(VitalsRequest request) {
    List<Observation> observations = new ArrayList<>();
    Date effectiveDate =
        Date.from(request.effectiveDate().atStartOfDay(ZoneId.systemDefault()).toInstant());

    if (request.systolicBp() != null) {
      observations.add(
          build(request, "systolic", request.systolicBp().doubleValue(), effectiveDate));
    }
    if (request.diastolicBp() != null) {
      observations.add(
          build(request, "diastolic", request.diastolicBp().doubleValue(), effectiveDate));
    }
    if (request.weightKg() != null) {
      observations.add(build(request, "weight", request.weightKg(), effectiveDate));
    }
    if (request.spo2Percent() != null) {
      observations.add(build(request, "spo2", request.spo2Percent(), effectiveDate));
    }
    if (request.heartRateBpm() != null) {
      observations.add(
          build(request, "heartRate", request.heartRateBpm().doubleValue(), effectiveDate));
    }
    if (request.temperatureCelsius() != null) {
      observations.add(build(request, "temperature", request.temperatureCelsius(), effectiveDate));
    }
    return observations;
  }

  private Observation build(VitalsRequest request, String type, double value, Date effectiveDate) {
    String[] meta = VITAL_META.get(type);
    Observation obs = new Observation();
    obs.setStatus(Observation.ObservationStatus.FINAL);
    obs.setCode(
        new CodeableConcept()
            .addCoding(new Coding().setSystem(LOINC_SYSTEM).setCode(meta[0]).setDisplay(meta[1])));
    obs.setSubject(new Reference("Patient/" + request.patientId()));
    if (request.encounterId() != null) {
      obs.setEncounter(new Reference("Encounter/" + request.encounterId()));
    }
    obs.setEffective(new DateTimeType(effectiveDate));
    obs.setValue(
        new Quantity().setValue(value).setUnit(meta[2]).setSystem(UCUM_SYSTEM).setCode(meta[2]));
    return obs;
  }

  public ObservationResponse toResponse(Observation observation) {
    String loincCode =
        observation.getCode().getCoding().stream()
            .filter(coding -> LOINC_SYSTEM.equals(coding.getSystem()))
            .map(Coding::getCode)
            .findFirst()
            .orElse(null);
    Quantity quantity = observation.hasValueQuantity() ? observation.getValueQuantity() : null;
    return new ObservationResponse(
        FhirValues.idOf(observation),
        FhirValues.referenceId(observation.getSubject()),
        FhirValues.referenceId(observation.getEncounter()),
        vitalType(loincCode),
        loincCode,
        quantity != null && quantity.hasValue() ? quantity.getValue().doubleValue() : null,
        quantity != null ? quantity.getUnit() : null,
        observation.hasEffectiveDateTimeType()
            ? observation.getEffectiveDateTimeType().getValueAsString()
            : null);
  }

  private static String vitalType(String loincCode) {
    return VITAL_META.entrySet().stream()
        .filter(entry -> entry.getValue()[0].equals(loincCode))
        .map(Map.Entry::getKey)
        .findFirst()
        .orElse(null);
  }
}
