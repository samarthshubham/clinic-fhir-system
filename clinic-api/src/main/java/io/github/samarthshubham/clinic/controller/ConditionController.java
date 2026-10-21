package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.ConditionRequest;
import io.github.samarthshubham.clinic.dto.ConditionResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.service.ConditionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/conditions")
public class ConditionController {

  private final ConditionService conditionService;

  public ConditionController(ConditionService conditionService) {
    this.conditionService = conditionService;
  }

  @PostMapping
  public ResponseEntity<ConditionResponse> createCondition(
      @Valid @RequestBody ConditionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(conditionService.createCondition(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ConditionResponse> getCondition(@PathVariable @NotBlank String id) {
    return ResponseEntity.ok(conditionService.getCondition(id));
  }

  @GetMapping
  public ResponseEntity<PageResponse<ConditionResponse>> getConditionsForPatient(
      @RequestParam @NotBlank String patientId) {
    return ResponseEntity.ok(conditionService.getConditionsForPatient(patientId));
  }
}
