package io.github.samarthshubham.clinic.mapper;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.clinic.dto.PageResponse;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

class PageMapperTest {

  @Test
  void mapsOnlyResourcesOfTheRequestedType() {
    Bundle bundle = bundleOf(withId(new Patient(), "p1"), new OperationOutcome());

    PageResponse<String> page =
        PageMapper.toPage(bundle, Patient.class, p -> p.getIdElement().getIdPart());

    assertThat(page.items()).containsExactly("p1");
    assertThat(page.total()).isEqualTo(1);
  }

  @Test
  void usesTheServerTotalWhenPresent() {
    Bundle bundle = bundleOf(withId(new Patient(), "p1")).setTotal(42);

    assertThat(PageMapper.toPage(bundle, Patient.class, p -> p.getIdElement().getIdPart()).total())
        .isEqualTo(42);
  }

  @Test
  void emptyBundleGivesEmptyPage() {
    assertThat(PageMapper.toPage(new Bundle(), Patient.class, p -> p.getIdElement().getIdPart()))
        .isEqualTo(new PageResponse<String>(List.of(), 0));
  }

  private static <T extends Resource> T withId(T resource, String id) {
    resource.setId(id);
    return resource;
  }
}
