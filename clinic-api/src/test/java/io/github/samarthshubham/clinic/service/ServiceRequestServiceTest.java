package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.client.CrdClient;
import io.github.samarthshubham.clinic.client.CrdResult;
import io.github.samarthshubham.clinic.dto.CrdCard;
import io.github.samarthshubham.clinic.dto.ServiceRequestRequest;
import io.github.samarthshubham.clinic.dto.ServiceRequestResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.ServiceRequestMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceRequestServiceTest {

  private FhirClientStub fhir;
  private ServiceRequestService service;
  private CrdService crd;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    crd = mock(CrdService.class);
    when(crd.checkOrder(any(), any(), any())).thenReturn(CrdResult.none());
    service = new ServiceRequestService(fhir.client(), new ServiceRequestMapper(), crd);
  }

  @Test
  void createStoresTheCoverageInformationAndCardsFromOrderSign() {
    Extension coverageInformation = new Extension(CrdClient.COVERAGE_INFORMATION);
    coverageInformation.addExtension("covered", new CodeType("covered"));
    coverageInformation.addExtension("pa-needed", new CodeType("auth-needed"));
    CrdCard card = new CrdCard("Prior authorization needed", "warning", "Detail", "Praxis");
    when(crd.checkOrder(any(ServiceRequest.class), eq("p1"), eq("dr1")))
        .thenReturn(new CrdResult(List.of(coverageInformation), List.of(card)));

    ServiceRequestResponse order = service.createServiceRequest(mriRequest("p1", "dr1"));

    assertThat(order.coverage().covered()).isEqualTo("covered");
    assertThat(order.coverage().paNeeded()).isEqualTo("auth-needed");
    assertThat(order.cards()).containsExactly(card);
    assertThat(service.getServiceRequest(order.id()).cards()).containsExactly(card);
  }

  @Test
  void createSavesTheOrderWithACoverageUnknownCardWhenCrdFailsOpen() {
    when(crd.checkOrder(any(), any(), any())).thenReturn(CrdResult.coverageUnknown());

    ServiceRequestResponse order = service.createServiceRequest(mriRequest("p1", null));

    assertThat(fhir.created()).singleElement().isInstanceOf(ServiceRequest.class);
    assertThat(order.coverage()).isNull();
    assertThat(order.cards()).containsExactly(CrdResult.COVERAGE_UNKNOWN);
  }

  private static ServiceRequestRequest mriRequest(String patientId, String practitionerId) {
    return new ServiceRequestRequest(
        patientId,
        null,
        practitionerId,
        "MRI lumbar spine without contrast",
        "http://www.ama-assn.org/go/cpt",
        "72148",
        "imaging",
        "active",
        "routine",
        LocalDate.now());
  }

  @Test
  void createStoresMappedOrderAndReturnsTheStoredCopy() {
    ServiceRequestResponse order =
        service.createServiceRequest(
            new ServiceRequestRequest(
                "p1",
                null,
                null,
                "Lipid panel",
                null,
                null,
                "laboratory",
                "active",
                "routine",
                LocalDate.now()));

    assertThat(fhir.created()).singleElement().isInstanceOf(ServiceRequest.class);
    assertThat(order)
        .isEqualTo(
            new ServiceRequestResponse(
                "1",
                "p1",
                null,
                null,
                "Lipid panel",
                null,
                null,
                "laboratory",
                "active",
                "routine",
                LocalDate.now(),
                null,
                List.of()));
  }

  @Test
  void getReturnsStoredOrder() {
    ServiceRequest stored = new ServiceRequest();
    fhir.givenStored(stored, "sr1");

    assertThat(service.getServiceRequest("sr1").id()).isEqualTo("sr1");
  }

  @Test
  void findReturnsStoredFhirOrder() {
    fhir.givenStored(new ServiceRequest(), "sr1");

    assertThat(service.findServiceRequest("sr1").getIdElement().getIdPart()).isEqualTo("sr1");
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
    ServiceRequest stored = new ServiceRequest();
    stored.setId("sr1");
    fhir.givenSearchResult(ServiceRequest.class, bundleOf(stored));

    assertThat(service.getServiceRequestsForPatient("p1", status).items())
        .extracting(ServiceRequestResponse::id)
        .containsExactly("sr1");
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
    service.findPendingServiceRequestsForPatient("p1");

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
