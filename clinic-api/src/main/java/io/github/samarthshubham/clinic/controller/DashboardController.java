package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.DashboardResponse;
import io.github.samarthshubham.clinic.service.DashboardService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @PreAuthorize("hasAuthority('clinical:read')")
  @GetMapping("/{patientId}")
  public ResponseEntity<DashboardResponse> getDashboard(@PathVariable @NotBlank String patientId) {
    return ResponseEntity.ok(dashboardService.getDashboard(patientId));
  }
}
