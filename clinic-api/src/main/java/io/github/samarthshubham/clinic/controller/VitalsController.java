package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.ObservationResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.dto.VitalsRequest;
import io.github.samarthshubham.clinic.security.PatientAccess;
import io.github.samarthshubham.clinic.service.VitalsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vitals")
public class VitalsController {

  private final VitalsService vitalsService;
  private final PatientAccess patientAccess;

  public VitalsController(VitalsService vitalsService, PatientAccess patientAccess) {
    this.vitalsService = vitalsService;
    this.patientAccess = patientAccess;
  }

  @PreAuthorize("hasAuthority('vitals:write') or @patientAccess.isSelf(#request.patientId())")
  @PostMapping
  public ResponseEntity<List<ObservationResponse>> recordVitals(
      @Valid @RequestBody VitalsRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(vitalsService.recordVitals(request, patientAccess.isPatient()));
  }

  @PreAuthorize("hasAuthority('clinical:read') or @patientAccess.isSelf(#patientId)")
  @GetMapping
  public ResponseEntity<PageResponse<ObservationResponse>> getVitals(
      @RequestParam @NotBlank String patientId, @RequestParam(required = false) String type) {
    return ResponseEntity.ok(vitalsService.getVitals(patientId, type));
  }
}
