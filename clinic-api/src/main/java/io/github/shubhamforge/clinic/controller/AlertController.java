package io.github.shubhamforge.clinic.controller;

import io.github.shubhamforge.clinic.service.HighBpAlertService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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
  public ResponseEntity<Void> onBpSubscriptionNotification() {
    highBpAlertService.handleNotification();
    return ResponseEntity.noContent().build();
  }
}
