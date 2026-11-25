package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.config.JwtConfig;
import io.github.samarthshubham.clinic.dto.DocumentationRequest;
import io.github.samarthshubham.clinic.dto.DocumentationResponse;
import io.github.samarthshubham.clinic.service.dtr.DtrService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-requests/{id}/documentation")
public class DocumentationController {

  private final DtrService dtrService;

  public DocumentationController(DtrService dtrService) {
    this.dtrService = dtrService;
  }

  @PreAuthorize("hasAuthority('clinical:read')")
  @GetMapping
  public ResponseEntity<DocumentationResponse> getDocumentation(@PathVariable @NotBlank String id) {
    return ResponseEntity.ok(dtrService.getDocumentation(id));
  }

  @PreAuthorize("hasAuthority('order:write')")
  @PutMapping
  public ResponseEntity<DocumentationResponse> saveDocumentation(
      @PathVariable @NotBlank String id,
      @Valid @RequestBody DocumentationRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(dtrService.saveDocumentation(id, request, practitionerId(jwt)));
  }

  @PreAuthorize("hasAuthority('order:write')")
  @PostMapping("/complete")
  public ResponseEntity<DocumentationResponse> completeDocumentation(
      @PathVariable @NotBlank String id,
      @Valid @RequestBody(required = false) DocumentationRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(dtrService.completeDocumentation(id, request, practitionerId(jwt)));
  }

  private static String practitionerId(Jwt jwt) {
    return jwt == null ? null : jwt.getClaimAsString(JwtConfig.PRACTITIONER_ID_CLAIM);
  }
}
