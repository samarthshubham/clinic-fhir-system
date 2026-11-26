package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.payer.interop.PlanNetDirectory;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class DirectoryServiceTest {
  private static final FhirContext FHIR = FhirContext.forR4Cached();

  private final PlanNetDirectory publisher = mock(PlanNetDirectory.class);
  private final DirectoryService directory = new DirectoryService(publisher);

  @Test
  void matchesPublishedSpecialtyAndLocation() {
    when(publisher.practitionerRoles(0)).thenReturn(directoryPage());
    assertThat(directory.search("Internal", "Springfield"))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.name()).isEqualTo("Aisha Patel, MD");
              assertThat(row.specialties()).containsExactly("Internal Medicine");
              assertThat(row.organization()).isEqualTo("Praxis Clinic");
              assertThat(row.address()).contains("400 Example Avenue");
            });
    assertThat(directory.search("Internal", "Chicago")).isEmpty();
    assertThat(directory.search("Neurology", "Springfield")).isEmpty();
  }

  @Test
  void skipsInactiveOrUnresolvedProviders() {
    var page = directoryPage();
    ((PractitionerRole) page.getEntryFirstRep().getResource()).setActive(false);
    when(publisher.practitionerRoles(0)).thenReturn(page);
    assertThat(directory.search("", "")).isEmpty();

    page = directoryPage();
    page.getEntry().remove(1);
    when(publisher.practitionerRoles(0)).thenReturn(page);
    assertThat(directory.search("", "")).isEmpty();
  }

  private static Bundle directoryPage() {
    return bundle(
        resource("practitioner-role-aisha", PractitionerRole.class),
        resource("practitioner", Practitioner.class),
        resource("location-clinic", Location.class),
        resource("organization-clinic", Organization.class));
  }

  private static Bundle bundle(Resource... resources) {
    var bundle = new Bundle().setType(Bundle.BundleType.SEARCHSET);
    for (Resource resource : resources) {
      var entry = bundle.addEntry().setResource(resource);
      entry
          .getSearch()
          .setMode(
              resource instanceof PractitionerRole || resources.length == 1
                  ? Bundle.SearchEntryMode.MATCH
                  : Bundle.SearchEntryMode.INCLUDE);
    }
    return bundle;
  }

  private static <T extends Resource> T resource(String name, Class<T> type) {
    try (var fixture =
        new ClassPathResource("fixtures/davinci/plan-net/" + name + ".json").getInputStream()) {
      return FHIR.newJsonParser().parseResource(type, fixture);
    } catch (java.io.IOException exception) {
      throw new java.io.UncheckedIOException(exception);
    }
  }
}
