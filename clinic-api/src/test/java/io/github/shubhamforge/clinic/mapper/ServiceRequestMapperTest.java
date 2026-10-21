package io.github.shubhamforge.clinic.mapper;

import static io.github.shubhamforge.clinic.support.FhirDates.localDate;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.shubhamforge.clinic.dto.ServiceRequestRequest;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.Test;

class ServiceRequestMapperTest {

  private static final LocalDate AUTHORED = LocalDate.of(2026, 3, 3);

  private final ServiceRequestMapper mapper = new ServiceRequestMapper();

  @Test
  void mapsOrderFieldsAndReferences() {
    ServiceRequest order =
        mapper.toFhir(
            new ServiceRequestRequest(
                "p1", "e1", "dr1", "Lipid panel", "laboratory", "active", "urgent", AUTHORED));

    assertThat(order.getSubject().getReference()).isEqualTo("Patient/p1");
    assertThat(order.getEncounter().getReference()).isEqualTo("Encounter/e1");
    assertThat(order.getRequester().getReference()).isEqualTo("Practitioner/dr1");
    assertThat(order.getCode().getText()).isEqualTo("Lipid panel");
    assertThat(order.getCategoryFirstRep().getText()).isEqualTo("laboratory");
    assertThat(order.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.ACTIVE);
    assertThat(order.getPriority()).isEqualTo(ServiceRequest.ServiceRequestPriority.URGENT);
    assertThat(localDate(order.getAuthoredOn())).isEqualTo(AUTHORED);
  }

  @Test
  void omitsEncounterAndRequesterWhenNotGiven() {
    ServiceRequest order =
        mapper.toFhir(
            new ServiceRequestRequest(
                "p1", null, null, "Chest X-ray", "imaging", "active", "routine", AUTHORED));

    assertThat(order.hasEncounter()).isFalse();
    assertThat(order.hasRequester()).isFalse();
  }
}
