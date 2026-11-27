package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.exception.UnauthorizedWebhookException;
import io.github.samarthshubham.clinic.service.pas.PasDecisionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class PasDecisionWebhookController {

  private final PasDecisionService decisions;

  public PasDecisionWebhookController(PasDecisionService decisions) {
    this.decisions = decisions;
  }

  @PostMapping("/api/alerts/prior-auth-subscription")
  public ResponseEntity<Void> onDecision(
      @RequestHeader(value = PasDecisionService.SECRET_HEADER, required = false) String secret) {
    if (!decisions.isTrustedWebhookSecret(secret)) {
      throw new UnauthorizedWebhookException();
    }
    log.debug("Authenticated PAS decision notification received");
    decisions.refreshPending();
    return ResponseEntity.noContent().build();
  }
}
