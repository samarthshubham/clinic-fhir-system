package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.EncounterRequest;
import io.github.samarthshubham.clinic.service.EncounterService;
import jakarta.validation.Valid;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
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
@RequestMapping("/api/encounters")
public class EncounterController {

  private final EncounterService encounterService;

  public EncounterController(EncounterService encounterService) {
    this.encounterService = encounterService;
  }

  @PostMapping
  public ResponseEntity<Encounter> createEncounter(@Valid @RequestBody EncounterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(encounterService.createEncounter(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Encounter> getEncounter(@PathVariable String id) {
    return ResponseEntity.ok(encounterService.getEncounter(id));
  }

  @GetMapping
  public ResponseEntity<Bundle> getEncountersForPatient(@RequestParam String patientId) {
    return ResponseEntity.ok(encounterService.getEncountersForPatient(patientId));
  }
}
