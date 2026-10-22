package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.AlertResponse;
import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import java.time.Instant;
import java.util.Date;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Flag;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
public class FlagMapper {

  public static final String ALERT_TYPE_SYSTEM = "http://praxis.clinic/fhir/CodeSystem/alert-type";
  public static final String HIGH_SYSTOLIC_BP = "high-systolic-bp";
  public static final String ALERT_IDENTIFIER_SYSTEM =
      "http://praxis.clinic/fhir/identifier/bp-alert";
  public static final String ALERT_STATUS_TAG_SYSTEM =
      "http://praxis.clinic/fhir/CodeSystem/alert-status";
  static final String FLAG_DETAIL_URL = "http://hl7.org/fhir/StructureDefinition/flag-detail";
  private static final String FLAG_CATEGORY_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/flag-category";

  public Flag toFhir(ClinicianAlert alert, Instant raisedAt) {
    Flag flag = new Flag();
    flag.addIdentifier().setSystem(ALERT_IDENTIFIER_SYSTEM).setValue(alertKey(alert));
    setStatus(flag, Flag.FlagStatus.ACTIVE);
    flag.addCategory()
        .addCoding()
        .setSystem(FLAG_CATEGORY_SYSTEM)
        .setCode("clinical")
        .setDisplay("Clinical");
    flag.getCode().addCoding().setSystem(ALERT_TYPE_SYSTEM).setCode(HIGH_SYSTOLIC_BP);
    flag.getCode().setText(alert.reason());
    flag.setSubject(new Reference(alert.patientReference()));
    flag.getPeriod().setStart(Date.from(raisedAt));
    flag.addExtension(FLAG_DETAIL_URL, new Reference(alert.observationReference()));
    return flag;
  }

  public void setStatus(Flag flag, Flag.FlagStatus status) {
    flag.setStatus(status);
    flag.getMeta().getTag().removeIf(tag -> ALERT_STATUS_TAG_SYSTEM.equals(tag.getSystem()));
    flag.getMeta().addTag(ALERT_STATUS_TAG_SYSTEM, status.toCode(), null);
  }

  public String alertKey(ClinicianAlert alert) {
    return new IdType(alert.observationReference()).getIdPart();
  }

  public AlertResponse toResponse(Flag flag) {
    Extension detail = flag.getExtensionByUrl(FLAG_DETAIL_URL);
    String observationId =
        detail != null && detail.getValue() instanceof Reference reference
            ? FhirValues.referenceId(reference, "Observation")
            : null;
    return new AlertResponse(
        FhirValues.idOf(flag),
        FhirValues.referenceId(flag.getSubject(), "Patient"),
        observationId,
        flag.getCode().getText(),
        FhirValues.offsetDateTime(flag.getPeriod().getStartElement()),
        flag.hasStatus() ? flag.getStatus().toCode() : null);
  }
}
