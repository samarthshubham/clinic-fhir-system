package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.EncounterRequest;
import io.github.samarthshubham.clinic.dto.EncounterResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.service.EncounterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

  @PreAuthorize("hasAuthority('encounter:write')")
  @PostMapping
  public ResponseEntity<EncounterResponse> createEncounter(
      @Valid @RequestBody EncounterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(encounterService.createEncounter(request));
  }

  @PreAuthorize("hasAuthority('clinical:read')")
  @GetMapping("/{id}")
  public ResponseEntity<EncounterResponse> getEncounter(@PathVariable @NotBlank String id) {
    return ResponseEntity.ok(encounterService.getEncounter(id));
  }

  @PreAuthorize("hasAuthority('clinical:read')")
  @GetMapping
  public ResponseEntity<PageResponse<EncounterResponse>> getEncountersForPatient(
      @RequestParam @NotBlank String patientId) {
    return ResponseEntity.ok(encounterService.getEncountersForPatient(patientId));
  }
}
