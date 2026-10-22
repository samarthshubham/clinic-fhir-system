package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.AlertResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.exception.UnauthorizedWebhookException;
import io.github.samarthshubham.clinic.service.AlertService;
import io.github.samarthshubham.clinic.service.HighBpAlertService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

  private final HighBpAlertService highBpAlertService;
  private final AlertService alertService;

  public AlertController(HighBpAlertService highBpAlertService, AlertService alertService) {
    this.highBpAlertService = highBpAlertService;
    this.alertService = alertService;
  }

  @PreAuthorize("hasAuthority('alert:read')")
  @GetMapping
  public ResponseEntity<PageResponse<AlertResponse>> getAlerts(
      @RequestParam(required = false) @Pattern(regexp = "active|inactive") String status) {
    return ResponseEntity.ok(alertService.getAlerts(status));
  }

  @PreAuthorize("hasAuthority('alert:write')")
  @PostMapping("/{id}/acknowledge")
  public ResponseEntity<AlertResponse> acknowledge(@PathVariable @NotBlank String id) {
    return ResponseEntity.ok(alertService.acknowledge(id));
  }

  @PostMapping("/bp-subscription")
  public ResponseEntity<Void> onBpSubscriptionNotification(
      @RequestHeader(value = HighBpAlertService.SECRET_HEADER, required = false) String secret) {
    if (!highBpAlertService.isTrustedWebhookSecret(secret)) {
      throw new UnauthorizedWebhookException();
    }
    highBpAlertService.handleNotification();
    return ResponseEntity.noContent().build();
  }
}
