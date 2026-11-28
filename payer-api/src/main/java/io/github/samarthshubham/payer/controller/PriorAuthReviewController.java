package io.github.samarthshubham.payer.controller;

import io.github.samarthshubham.payer.dto.PriorAuthDecisionRequest;
import io.github.samarthshubham.payer.dto.PriorAuthReviewResponse;
import io.github.samarthshubham.payer.service.PriorAuthReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prior-auths/{claimId}")
public class PriorAuthReviewController {

  private final PriorAuthReviewService service;

  public PriorAuthReviewController(PriorAuthReviewService service) {
    this.service = service;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('priorauth:read')")
  public PriorAuthReviewResponse review(
      @PathVariable @Pattern(regexp = "[A-Za-z0-9.-]{1,64}") String claimId) {
    return service.review(claimId);
  }

  @PostMapping("/decision")
  @PreAuthorize("hasAuthority('priorauth:decide')")
  public PriorAuthReviewResponse decide(
      @PathVariable @Pattern(regexp = "[A-Za-z0-9.-]{1,64}") String claimId,
      @Valid @RequestBody PriorAuthDecisionRequest request,
      Authentication reviewer) {
    return service.decide(claimId, request, reviewer.getName());
  }
}
