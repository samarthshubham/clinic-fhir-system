package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.VitalsRequest;
import io.github.samarthshubham.clinic.service.VitalsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  public VitalsController(VitalsService vitalsService) {
    this.vitalsService = vitalsService;
  }

  @PostMapping
  public ResponseEntity<Bundle> recordVitals(@Valid @RequestBody VitalsRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(vitalsService.recordVitals(request));
  }

  @GetMapping
  public ResponseEntity<Bundle> getVitals(
      @RequestParam @NotBlank String patientId, @RequestParam(required = false) String type) {
    return ResponseEntity.ok(vitalsService.getVitals(patientId, type));
  }
}
