package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.PractitionerRequest;
import io.github.samarthshubham.clinic.dto.PractitionerResponse;
import io.github.samarthshubham.clinic.service.PractitionerService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/practitioners")
public class PractitionerController {

  private final PractitionerService practitionerService;

  public PractitionerController(PractitionerService practitionerService) {
    this.practitionerService = practitionerService;
  }

  @PreAuthorize("hasAuthority('practitioner:write')")
  @PostMapping
  public ResponseEntity<PractitionerResponse> createPractitioner(
      @Valid @RequestBody PractitionerRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(practitionerService.createPractitioner(request));
  }

  @PreAuthorize("hasAuthority('practitioner:read')")
  @GetMapping("/{id}")
  public ResponseEntity<PractitionerResponse> getPractitioner(@PathVariable @NotBlank String id) {
    return ResponseEntity.ok(practitionerService.getPractitioner(id));
  }
}
