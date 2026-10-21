package io.github.samarthshubham.clinic.mapper;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

final class FhirValues {

  private FhirValues() {}

  static String idOf(Resource resource) {
    return resource.getIdElement().getIdPart();
  }

  static String referenceId(Reference reference) {
    return reference.hasReference() ? reference.getReferenceElement().getIdPart() : null;
  }

  static String referenceId(Reference reference, String resourceType) {
    return resourceType.equals(reference.getReferenceElement().getResourceType())
        ? referenceId(reference)
        : null;
  }

  static LocalDate localDate(BaseDateTimeType value) {
    if (value == null
        || value.isEmpty()
        || value.getPrecision().compareTo(TemporalPrecisionEnum.DAY) < 0) {
      return null;
    }
    return LocalDate.parse(value.getValueAsString().substring(0, 10));
  }

  static OffsetDateTime offsetDateTime(BaseDateTimeType value) {
    if (value == null || value.isEmpty()) {
      return null;
    }
    return OffsetDateTime.ofInstant(value.getValue().toInstant(), value.getTimeZone().toZoneId());
  }

  static String given(List<HumanName> names) {
    return names.isEmpty() || !names.get(0).hasGiven()
        ? null
        : names.get(0).getGivenAsSingleString();
  }

  static String family(List<HumanName> names) {
    return names.isEmpty() ? null : names.get(0).getFamily();
  }

  static String telecom(List<ContactPoint> telecom, ContactPoint.ContactPointSystem system) {
    return telecom.stream()
        .filter(cp -> cp.getSystem() == system)
        .map(ContactPoint::getValue)
        .findFirst()
        .orElse(null);
  }
}
