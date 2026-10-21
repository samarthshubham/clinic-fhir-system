package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.DiagnosticReportRequest;
import io.github.samarthshubham.clinic.service.DiagnosticReportService;
import jakarta.validation.Valid;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnostic-reports")
public class DiagnosticReportController {

  private final DiagnosticReportService diagnosticReportService;

  public DiagnosticReportController(DiagnosticReportService diagnosticReportService) {
    this.diagnosticReportService = diagnosticReportService;
  }

  @PostMapping
  public ResponseEntity<DiagnosticReport> createDiagnosticReport(
      @Valid @RequestBody DiagnosticReportRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(diagnosticReportService.createDiagnosticReport(request));
  }

  @GetMapping
  public ResponseEntity<Bundle> getDiagnosticReportsForPatient(@RequestParam String patientId) {
    return ResponseEntity.ok(diagnosticReportService.getDiagnosticReportsForPatient(patientId));
  }
}
