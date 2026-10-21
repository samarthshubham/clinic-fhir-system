package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.PatientRequest;
import io.github.samarthshubham.clinic.dto.SnapshotResponse;
import io.github.samarthshubham.clinic.dto.TimelineEvent;
import io.github.samarthshubham.clinic.dto.TrendsResponse;
import io.github.samarthshubham.clinic.service.PatientService;
import io.github.samarthshubham.clinic.service.SnapshotService;
import io.github.samarthshubham.clinic.service.SummaryService;
import io.github.samarthshubham.clinic.service.TimelineService;
import io.github.samarthshubham.clinic.service.TrendsService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

  private final PatientService patientService;
  private final SummaryService summaryService;
  private final SnapshotService snapshotService;
  private final TrendsService trendsService;
  private final TimelineService timelineService;

  public PatientController(
      PatientService patientService,
      SummaryService summaryService,
      SnapshotService snapshotService,
      TrendsService trendsService,
      TimelineService timelineService) {
    this.patientService = patientService;
    this.summaryService = summaryService;
    this.snapshotService = snapshotService;
    this.trendsService = trendsService;
    this.timelineService = timelineService;
  }

  @PostMapping
  public ResponseEntity<Patient> createPatient(@Valid @RequestBody PatientRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(patientService.createPatient(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Patient> getPatient(@PathVariable String id) {
    return ResponseEntity.ok(patientService.getPatient(id));
  }

  @GetMapping
  public ResponseEntity<Bundle> searchPatients(
      @RequestParam(required = false) String name, @RequestParam(required = false) LocalDate dob) {
    return ResponseEntity.ok(patientService.searchPatients(name, dob));
  }

  @GetMapping("/{id}/summary")
  public ResponseEntity<Bundle> getSummary(@PathVariable String id) {
    return ResponseEntity.ok(summaryService.getSummary(id));
  }

  @GetMapping("/{id}/snapshot")
  public ResponseEntity<SnapshotResponse> getSnapshot(@PathVariable String id) {
    return ResponseEntity.ok(snapshotService.getSnapshot(id));
  }

  @GetMapping("/{id}/trends")
  public ResponseEntity<TrendsResponse> getTrends(
      @PathVariable String id,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String period) {
    return ResponseEntity.ok(trendsService.getTrends(id, type, period));
  }

  @GetMapping("/{id}/timeline")
  public ResponseEntity<List<TimelineEvent>> getTimeline(
      @PathVariable String id,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String before,
      @RequestParam(required = false) String types) {
    return ResponseEntity.ok(timelineService.getTimeline(id, limit, before, types));
  }
}
