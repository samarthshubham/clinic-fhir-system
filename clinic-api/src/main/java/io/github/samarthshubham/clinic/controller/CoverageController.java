package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.CoverageRequest;
import io.github.samarthshubham.clinic.dto.CoverageResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.service.CoverageService;
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
@RequestMapping("/api/coverages")
public class CoverageController {

  private final CoverageService coverageService;

  public CoverageController(CoverageService coverageService) {
    this.coverageService = coverageService;
  }

  @PreAuthorize("hasAuthority('coverage:write')")
  @PostMapping
  public ResponseEntity<CoverageResponse> createCoverage(
      @Valid @RequestBody CoverageRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(coverageService.createCoverage(request));
  }

  @PreAuthorize("hasAuthority('coverage:read')")
  @GetMapping("/{id}")
  public ResponseEntity<CoverageResponse> getCoverage(@PathVariable @NotBlank String id) {
    return ResponseEntity.ok(coverageService.getCoverage(id));
  }

  @PreAuthorize("hasAuthority('coverage:read') or @patientAccess.isSelf(#patientId)")
  @GetMapping
  public ResponseEntity<PageResponse<CoverageResponse>> getCoveragesForPatient(
      @RequestParam @NotBlank String patientId) {
    return ResponseEntity.ok(coverageService.getCoveragesForPatient(patientId));
  }
}
