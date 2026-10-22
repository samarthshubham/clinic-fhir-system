package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.GoalRequest;
import io.github.samarthshubham.clinic.dto.GoalResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.service.GoalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
@RequestMapping("/api/goals")
public class GoalController {

  private final GoalService goalService;

  public GoalController(GoalService goalService) {
    this.goalService = goalService;
  }

  @PreAuthorize("hasAuthority('careplan:write')")
  @PostMapping
  public ResponseEntity<GoalResponse> createGoal(@Valid @RequestBody GoalRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(goalService.createGoal(request));
  }

  @PreAuthorize("hasAuthority('clinical:read')")
  @GetMapping
  public ResponseEntity<PageResponse<GoalResponse>> getGoalsForPatient(
      @RequestParam @NotBlank String patientId) {
    return ResponseEntity.ok(goalService.getGoalsForPatient(patientId));
  }
}
