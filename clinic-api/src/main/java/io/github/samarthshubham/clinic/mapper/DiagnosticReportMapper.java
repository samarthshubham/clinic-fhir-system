package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.dto.DiagnosticReportRequest;
import java.time.ZoneId;
import java.util.Date;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticReportMapper {

  public DiagnosticReport toFhir(DiagnosticReportRequest request) {
    DiagnosticReport report = new DiagnosticReport();
    report.setSubject(new Reference("Patient/" + request.patientId()));
    report.setStatus(DiagnosticReport.DiagnosticReportStatus.fromCode(request.status()));
    report.setCode(new CodeableConcept().setText(request.title()));
    if (request.encounterId() != null) {
      report.setEncounter(new Reference("Encounter/" + request.encounterId()));
    }
    if (request.serviceRequestId() != null) {
      report.addBasedOn(new Reference("ServiceRequest/" + request.serviceRequestId()));
    }
    Date issued = Date.from(request.issued().atStartOfDay(ZoneId.systemDefault()).toInstant());
    report.setIssued(issued);
    if (request.conclusion() != null) {
      report.setConclusion(request.conclusion());
    }
    if (request.resultIds() != null) {
      request.resultIds().forEach(rid -> report.addResult(new Reference("Observation/" + rid)));
    }
    return report;
  }
}
