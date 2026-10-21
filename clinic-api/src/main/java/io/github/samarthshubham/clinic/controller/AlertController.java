package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.exception.UnauthorizedWebhookException;
import io.github.samarthshubham.clinic.service.HighBpAlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

  private final HighBpAlertService highBpAlertService;

  public AlertController(HighBpAlertService highBpAlertService) {
    this.highBpAlertService = highBpAlertService;
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
