package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import io.github.samarthshubham.clinic.dto.DiagnosticReportRequest;
import io.github.samarthshubham.clinic.dto.DiagnosticReportResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.DiagnosticReportMapper;
import io.github.samarthshubham.clinic.mapper.PageMapper;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DiagnosticReportService {

  private final IGenericClient fhirClient;
  private final DiagnosticReportMapper diagnosticReportMapper;
  private final ServiceRequestService serviceRequestService;

  public DiagnosticReportService(
      IGenericClient fhirClient,
      DiagnosticReportMapper diagnosticReportMapper,
      ServiceRequestService serviceRequestService) {
    this.fhirClient = fhirClient;
    this.diagnosticReportMapper = diagnosticReportMapper;
    this.serviceRequestService = serviceRequestService;
  }

  public DiagnosticReportResponse createDiagnosticReport(DiagnosticReportRequest request) {
    var outcome = fhirClient.create().resource(diagnosticReportMapper.toFhir(request)).execute();
    String id = outcome.getId().getIdPart();
    if (request.serviceRequestId() != null) {
      try {
        serviceRequestService.complete(request.serviceRequestId());
      } catch (ResourceNotFoundException e) {
        log.warn(
            "ServiceRequest {} not found when completing for DiagnosticReport — skipping",
            request.serviceRequestId());
      }
    }
    return diagnosticReportMapper.toResponse(
        fhirClient.read().resource(DiagnosticReport.class).withId(id).execute());
  }

  public DiagnosticReport getDiagnosticReport(String id) {
    try {
      return fhirClient.read().resource(DiagnosticReport.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException e) {
      throw new ResourceNotFoundException("DiagnosticReport", id);
    }
  }

  public PageResponse<DiagnosticReportResponse> getDiagnosticReportsForPatient(String patientId) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(DiagnosticReport.class)
            .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
            .returnBundle(Bundle.class)
            .execute();
    return PageMapper.toPage(bundle, DiagnosticReport.class, diagnosticReportMapper::toResponse);
  }

  public Bundle getMostRecentDiagnosticReport(String patientId) {
    return fhirClient
        .search()
        .forResource(DiagnosticReport.class)
        .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
        .sort()
        .descending("issued")
        .count(1)
        .returnBundle(Bundle.class)
        .execute();
  }
}
