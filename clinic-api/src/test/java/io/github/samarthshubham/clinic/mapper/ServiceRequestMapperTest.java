package io.github.samarthshubham.clinic.mapper;

import static io.github.samarthshubham.clinic.support.FhirDates.localDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.clinic.dto.ServiceRequestRequest;
import io.github.samarthshubham.clinic.dto.ServiceRequestResponse;
import java.time.LocalDate;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  @ParameterizedTest
  @CsvSource({
    "draft,routine,DRAFT,ROUTINE",
    "completed,asap,COMPLETED,ASAP",
    "revoked,stat,REVOKED,STAT"
  })
  void mapsEveryStatusAndPriority(
      String status,
      String priority,
      ServiceRequest.ServiceRequestStatus expectedStatus,
      ServiceRequest.ServiceRequestPriority expectedPriority) {
    ServiceRequest order =
        mapper.toFhir(
            new ServiceRequestRequest(
                "p1", null, null, "CBC", "laboratory", status, priority, AUTHORED));

    assertThat(order.getStatus()).isEqualTo(expectedStatus);
    assertThat(order.getPriority()).isEqualTo(expectedPriority);
  }

  @ParameterizedTest
  @CsvSource({"open,routine", "active,whenever"})
  void unknownStatusOrPriorityIsRejected(String status, String priority) {
    ServiceRequestRequest request =
        new ServiceRequestRequest(
            "p1", null, null, "CBC", "laboratory", status, priority, AUTHORED);

    assertThatThrownBy(() -> mapper.toFhir(request)).isInstanceOf(FHIRException.class);
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

  @Test
  void toResponseIsTheReverseOfToFhir() {
    ServiceRequest serviceRequest =
        mapper.toFhir(
            new ServiceRequestRequest(
                "p1", "e1", "dr1", "Lipid panel", "laboratory", "active", "urgent", AUTHORED));
    serviceRequest.setId("sr1");

    assertThat(mapper.toResponse(serviceRequest))
        .isEqualTo(
            new ServiceRequestResponse(
                "sr1",
                "p1",
                "e1",
                "dr1",
                "Lipid panel",
                "laboratory",
                "active",
                "urgent",
                AUTHORED));
  }

  @Test
  void toResponseLeavesMissingFieldsNull() {
    ServiceRequest serviceRequest = new ServiceRequest();
    serviceRequest.setId("sr1");

    assertThat(mapper.toResponse(serviceRequest))
        .isEqualTo(
            new ServiceRequestResponse("sr1", null, null, null, null, null, null, null, null));
  }
}
