package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.shubhamforge.clinic.dto.ServiceRequestRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.ServiceRequestMapper;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceRequestServiceTest {

  private FhirClientStub fhir;
  private ServiceRequestService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new ServiceRequestService(fhir.client(), new ServiceRequestMapper());
  }

  @Test
  void createStoresMappedOrderAndReturnsTheStoredCopy() {
    ServiceRequest order =
        service.createServiceRequest(
            new ServiceRequestRequest(
                "p1",
                null,
                null,
                "Lipid panel",
                "laboratory",
                "active",
                "routine",
                LocalDate.now()));

    assertThat(fhir.created()).singleElement().isNotSameAs(order);
    assertThat(order.getIdElement().getIdPart()).isEqualTo("1");
    assertThat(order.getCode().getText()).isEqualTo("Lipid panel");
  }

  @Test
  void getReturnsStoredOrder() {
    ServiceRequest stored = new ServiceRequest();
    fhir.givenStored(stored, "sr1");

    assertThat(service.getServiceRequest("sr1").getIdElement().getIdPart()).isEqualTo("sr1");
  }

  @Test
  void getUnknownOrderThrowsNotFound() {
    assertThatThrownBy(() -> service.getServiceRequest("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("ServiceRequest with id 'missing' not found");
  }

  @Test
  void getPropagatesOtherServerErrors() {
    fhir.givenReadFails(ServiceRequest.class, "x", new InternalErrorException("FHIR server error"));

    assertThatThrownBy(() -> service.getServiceRequest("x"))
        .isInstanceOf(InternalErrorException.class);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "  "})
  void listWithoutStatusReturnsAllOrders(String status) {
    Bundle orders = bundleOf(new ServiceRequest());
    fhir.givenSearchResult(ServiceRequest.class, orders);

    assertThat(service.getServiceRequestsForPatient("p1", status)).isSameAs(orders);
    assertThat(fhir.lastSearchCriteria(ServiceRequest.class))
        .containsExactlyInAnyOrder("subject=Patient/p1");
  }

  @Test
  void listFiltersByStatus() {
    service.getServiceRequestsForPatient("p1", "completed");

    assertThat(fhir.lastSearchCriteria(ServiceRequest.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", "status=completed");
  }

  @Test
  void pendingOrdersAreTheFiveMostRecentActiveOnes() {
    service.getPendingServiceRequestsForPatient("p1");

    assertThat(fhir.lastSearchCriteria(ServiceRequest.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", "status=active", "_count=5");
  }

  @Test
  void completeMarksTheOrderCompleted() {
    ServiceRequest order =
        new ServiceRequest().setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
    fhir.givenStored(order, "sr1");

    service.complete("sr1");

    assertThat(fhir.updated()).hasSize(1);
    assertThat(fhir.stored(ServiceRequest.class, "sr1").getStatus())
        .isEqualTo(ServiceRequest.ServiceRequestStatus.COMPLETED);
  }

  @Test
  void completingUnknownOrderThrowsNotFound() {
    assertThatThrownBy(() -> service.complete("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("ServiceRequest with id 'missing' not found");
    assertThat(fhir.updated()).isEmpty();
  }
}
