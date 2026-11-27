package io.github.samarthshubham.payer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.payer.interop.PublicPayerCatalog;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

class DirectoryServiceTest {
  private static final FhirContext FHIR = FhirContext.forR4Cached();

  private final PublicPayerCatalog publisher = mock(PublicPayerCatalog.class);
  private final DirectoryService directory = new DirectoryService(publisher);

  @Test
  void matchesPublishedSpecialtyAndLocation() {
    when(publisher.practitionerRoles(0)).thenReturn(directoryPage());
    assertThat(directory.search("Internal", "Springfield"))
        .singleElement()
        .satisfies(
            row -> {
              assertThat(row.name()).isEqualTo("Aisha Patel, MD");
              assertThat(row.specialties()).containsExactly("Internal Medicine Physician");
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

  @Test
  void followsTheOffsetEvenWhenThePageHasNoPublishedMatches() {
    var firstPage = bundle();
    firstPage.addLink().setRelation(Bundle.LINK_NEXT).setUrl("/fhir/PractitionerRole?_offset=50");
    when(publisher.practitionerRoles(0)).thenReturn(firstPage);
    when(publisher.practitionerRoles(50)).thenReturn(directoryPage());

    assertThat(directory.search("", ""))
        .extracting(row -> row.name())
        .containsExactly("Aisha Patel, MD");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {"/fhir/PractitionerRole", "?_offset=0", "?_offset=bad", "?_offset=2&_offset=3"})
  void stopsWhenTheNextOffsetCannotAdvance(String nextUrl) {
    var page = directoryPage();
    page.addLink().setRelation(Bundle.LINK_NEXT).setUrl(nextUrl);
    when(publisher.practitionerRoles(0)).thenReturn(page);

    assertThat(directory.search("", "")).hasSize(1);
    verify(publisher).practitionerRoles(0);
    verifyNoMoreInteractions(publisher);
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
