package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.MedicationRequest;
import io.github.samarthshubham.clinic.service.MedicationService;
import jakarta.validation.Valid;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/medications")
public class MedicationController {

  private final MedicationService medicationService;

  public MedicationController(MedicationService medicationService) {
    this.medicationService = medicationService;
  }

  @PostMapping
  public ResponseEntity<MedicationStatement> createMedication(
      @Valid @RequestBody MedicationRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(medicationService.createMedication(request));
  }

  @GetMapping
  public ResponseEntity<Bundle> getMedicationsForPatient(@RequestParam String patientId) {
    return ResponseEntity.ok(medicationService.getMedicationsForPatient(patientId));
  }
}
