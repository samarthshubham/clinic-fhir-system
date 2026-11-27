package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.DirectoryProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

public final class DirectoryMapper {

  private DirectoryMapper() {}

  public static List<DirectoryProvider> providers(Bundle bundle, String specialty, String city) {
    Map<String, Resource> resources = new LinkedHashMap<>();
    for (var entry : bundle.getEntry()) {
      Resource resource = entry.getResource();
      if (resource != null && resource.hasId()) {
        resources.put(resource.fhirType() + "/" + resource.getIdElement().getIdPart(), resource);
      }
    }
    return resources.values().stream()
        .filter(PractitionerRole.class::isInstance)
        .map(PractitionerRole.class::cast)
        .filter(role -> !role.hasActive() || role.getActive())
        .flatMap(role -> rows(role, resources, specialty, city))
        .toList();
  }

  private static Stream<DirectoryProvider> rows(
      PractitionerRole role, Map<String, Resource> resources, String specialty, String city) {
    Resource provider = resolve(resources, role.getPractitioner());
    if (!(provider instanceof Practitioner practitioner)
        || (practitioner.hasActive() && !practitioner.getActive())) {
      return Stream.empty();
    }
    List<Coding> codings =
        role.getSpecialty().stream().flatMap(concept -> concept.getCoding().stream()).toList();
    if (!specialty.isBlank()
        && codings.stream()
            .noneMatch(
                coding ->
                    matches(coding.getDisplay(), specialty)
                        || matches(coding.getCode(), specialty))) {
      return Stream.empty();
    }
    List<String> labels =
        codings.stream()
            .map(coding -> coding.hasDisplay() ? coding.getDisplay() : coding.getCode())
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    Resource employer = resolve(resources, role.getOrganization());
    String organization = employer instanceof Organization org ? org.getName() : null;
    return role.getLocation().stream()
        .map(location -> resolve(resources, location))
        .filter(Location.class::isInstance)
        .map(Location.class::cast)
        .filter(
            location ->
                !location.hasStatus() || location.getStatus() == Location.LocationStatus.ACTIVE)
        .filter(location -> city.isBlank() || matches(location.getAddress().getCity(), city))
        .map(
            location ->
                new DirectoryProvider(
                    role.getIdElement().getIdPart() + ":" + location.getIdElement().getIdPart(),
                    practitioner.getNameFirstRep().getNameAsSingleString(),
                    labels,
                    location.getName(),
                    address(location.getAddress()),
                    organization));
  }

  private static Resource resolve(Map<String, Resource> resources, Reference reference) {
    var id = reference.getReferenceElement();
    return resources.get(id.getResourceType() + "/" + id.getIdPart());
  }

  private static boolean matches(String value, String query) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
  }

  private static String address(Address address) {
    return Stream.concat(
            address.getLine().stream().map(StringType::getValue),
            Stream.of(address.getCity(), address.getState(), address.getPostalCode()))
        .filter(part -> part != null && !part.isBlank())
        .collect(Collectors.joining(", "));
  }
}
