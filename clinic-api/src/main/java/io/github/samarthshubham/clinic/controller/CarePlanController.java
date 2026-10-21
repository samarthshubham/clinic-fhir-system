package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.CarePlanRequest;
import io.github.samarthshubham.clinic.service.CarePlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CarePlan;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/care-plans")
public class CarePlanController {

  private final CarePlanService carePlanService;

  public CarePlanController(CarePlanService carePlanService) {
    this.carePlanService = carePlanService;
  }

  @PostMapping
  public ResponseEntity<CarePlan> createCarePlan(@Valid @RequestBody CarePlanRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(carePlanService.createCarePlan(request));
  }

  @GetMapping
  public ResponseEntity<Bundle> getCarePlansForPatient(@RequestParam @NotBlank String patientId) {
    return ResponseEntity.ok(carePlanService.getCarePlansForPatient(patientId));
  }
}
