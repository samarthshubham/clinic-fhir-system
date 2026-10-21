package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.dto.DiagnosticReportRequest;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.DiagnosticReportMapper;
import io.github.samarthshubham.clinic.mapper.ServiceRequestMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiagnosticReportServiceTest {

  private FhirClientStub fhir;
  private DiagnosticReportService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    ServiceRequestService orders =
        new ServiceRequestService(fhir.client(), new ServiceRequestMapper());
    service = new DiagnosticReportService(fhir.client(), new DiagnosticReportMapper(), orders);
  }

  @Test
  void createStoresMappedReportAndReturnsTheStoredCopy() {
    DiagnosticReport report = service.createDiagnosticReport(request(null));

    assertThat(fhir.created()).singleElement().isNotSameAs(report);
    assertThat(report.getIdElement().getIdPart()).isEqualTo("1");
    assertThat(report.getCode().getText()).isEqualTo("Lipid panel");
    assertThat(fhir.updated()).isEmpty();
  }

  @Test
  void createCompletesTheLinkedOrder() {
    ServiceRequest order =
        new ServiceRequest().setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);
    fhir.givenStored(order, "sr1");

    service.createDiagnosticReport(request("sr1"));

    assertThat(fhir.updated()).hasSize(1);
    assertThat(fhir.stored(ServiceRequest.class, "sr1").getStatus())
        .isEqualTo(ServiceRequest.ServiceRequestStatus.COMPLETED);
  }

  @Test
  void otherFailureCompletingTheOrderPropagatesAfterTheReportIsCreated() {
    fhir.givenReadFails(ServiceRequest.class, "sr1", new InternalErrorException("down"));

    assertThatThrownBy(() -> service.createDiagnosticReport(request("sr1")))
        .isInstanceOf(InternalErrorException.class);
    assertThat(fhir.created()).hasSize(1);
    assertThat(fhir.updated()).isEmpty();
  }

  @Test
  void createStillSucceedsWhenLinkedOrderIsMissing() {
    DiagnosticReport report = service.createDiagnosticReport(request("missing"));

    assertThat(fhir.created()).singleElement().isNotSameAs(report);
    assertThat(fhir.updated()).isEmpty();
  }

  @Test
  void getReturnsStoredReport() {
    DiagnosticReport stored = new DiagnosticReport();
    fhir.givenStored(stored, "r1");

    assertThat(service.getDiagnosticReport("r1").getIdElement().getIdPart()).isEqualTo("r1");
  }

  @Test
  void getUnknownReportThrowsNotFound() {
    assertThatThrownBy(() -> service.getDiagnosticReport("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("DiagnosticReport with id 'missing' not found");
  }

  @Test
  void getPropagatesOtherServerErrors() {
    fhir.givenReadFails(
        DiagnosticReport.class, "x", new InternalErrorException("FHIR server error"));

    assertThatThrownBy(() -> service.getDiagnosticReport("x"))
        .isInstanceOf(InternalErrorException.class);
  }

  @Test
  void listsReportsForPatient() {
    Bundle reports = bundleOf(new DiagnosticReport());
    fhir.givenSearchResult(DiagnosticReport.class, reports);

    assertThat(service.getDiagnosticReportsForPatient("p1")).isSameAs(reports);
    assertThat(fhir.lastSearchCriteria(DiagnosticReport.class))
        .containsExactlyInAnyOrder("subject=Patient/p1");
  }

  @Test
  void mostRecentReportIsLatestByIssuedDate() {
    service.getMostRecentDiagnosticReport("p1");

    assertThat(fhir.lastSearchCriteria(DiagnosticReport.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", "_sort=-issued", "_count=1");
  }

  private static DiagnosticReportRequest request(String serviceRequestId) {
    return new DiagnosticReportRequest(
        "p1", null, serviceRequestId, "Lipid panel", "final", LocalDate.now(), null, null);
  }
}
