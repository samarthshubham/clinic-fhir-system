package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.PractitionerRequest;
import io.github.samarthshubham.clinic.service.PractitionerService;
import jakarta.validation.Valid;
import org.hl7.fhir.r4.model.Practitioner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

  @PostMapping
  public ResponseEntity<Practitioner> createPractitioner(
      @Valid @RequestBody PractitionerRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(practitionerService.createPractitioner(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<Practitioner> getPractitioner(@PathVariable String id) {
    return ResponseEntity.ok(practitionerService.getPractitioner(id));
  }
}
