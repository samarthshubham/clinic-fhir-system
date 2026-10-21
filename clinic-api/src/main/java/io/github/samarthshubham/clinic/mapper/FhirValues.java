package io.github.samarthshubham.clinic.mapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
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

  static LocalDate localDate(Date date) {
    return date == null ? null : date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
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
