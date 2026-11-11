package io.github.samarthshubham.payer.cdshooks;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.samarthshubham.payer.config.JwtConfig;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/cds-services", produces = MediaType.APPLICATION_JSON_VALUE)
public class CdsServicesController {

  private final CrdHookService crdHookService;

  public CdsServicesController(CrdHookService crdHookService) {
    this.crdHookService = crdHookService;
  }

  @GetMapping
  public ResponseEntity<CdsDiscovery> discovery() {
    return ResponseEntity.ok(crdHookService.discovery());
  }

  @PostMapping("/{id}")
  public ResponseEntity<JsonNode> callService(
      @PathVariable String id,
      @RequestBody(required = false) String body,
      @AuthenticationPrincipal Jwt token) {
    return ResponseEntity.ok(
        crdHookService.call(id, body, token.getClaimAsString(JwtConfig.CLIENT_ID_CLAIM)));
  }
}
