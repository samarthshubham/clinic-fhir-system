package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.dto.PatientRequest;
import io.github.samarthshubham.clinic.dto.PatientResponse;
import io.github.samarthshubham.clinic.dto.SnapshotResponse;
import io.github.samarthshubham.clinic.dto.TimelineEvent;
import io.github.samarthshubham.clinic.dto.TrendsResponse;
import io.github.samarthshubham.clinic.service.PatientService;
import io.github.samarthshubham.clinic.service.SnapshotService;
import io.github.samarthshubham.clinic.service.TimelineService;
import io.github.samarthshubham.clinic.service.TrendsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
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
  private final SnapshotService snapshotService;
  private final TrendsService trendsService;
  private final TimelineService timelineService;

  public PatientController(
      PatientService patientService,
      SnapshotService snapshotService,
      TrendsService trendsService,
      TimelineService timelineService) {
    this.patientService = patientService;
    this.snapshotService = snapshotService;
    this.trendsService = trendsService;
    this.timelineService = timelineService;
  }

  @PostMapping
  public ResponseEntity<PatientResponse> createPatient(@Valid @RequestBody PatientRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(patientService.createPatient(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<PatientResponse> getPatient(@PathVariable @NotBlank String id) {
    return ResponseEntity.ok(patientService.getPatient(id));
  }

  @GetMapping
  public ResponseEntity<PageResponse<PatientResponse>> searchPatients(
      @RequestParam(required = false) String name, @RequestParam(required = false) LocalDate dob) {
    return ResponseEntity.ok(patientService.searchPatients(name, dob));
  }

  @GetMapping("/{id}/snapshot")
  public ResponseEntity<SnapshotResponse> getSnapshot(@PathVariable @NotBlank String id) {
    return ResponseEntity.ok(snapshotService.getSnapshot(id));
  }

  @GetMapping("/{id}/trends")
  public ResponseEntity<TrendsResponse> getTrends(
      @PathVariable @NotBlank String id,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String period) {
    return ResponseEntity.ok(trendsService.getTrends(id, type, period));
  }

  @GetMapping("/{id}/timeline")
  public ResponseEntity<List<TimelineEvent>> getTimeline(
      @PathVariable @NotBlank String id,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) LocalDate before,
      @RequestParam(required = false) String types) {
    return ResponseEntity.ok(timelineService.getTimeline(id, limit, before, types));
  }
}
