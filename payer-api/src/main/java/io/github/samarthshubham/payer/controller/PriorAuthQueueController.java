package io.github.samarthshubham.payer.controller;

import io.github.samarthshubham.payer.dto.PageResponse;
import io.github.samarthshubham.payer.dto.PriorAuthQueueItem;
import io.github.samarthshubham.payer.service.PriorAuthQueueService;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prior-auths")
public class PriorAuthQueueController {

  private final PriorAuthQueueService queue;

  public PriorAuthQueueController(PriorAuthQueueService queue) {
    this.queue = queue;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('priorauth:read')")
  public ResponseEntity<PageResponse<PriorAuthQueueItem>> list(
      @RequestParam(defaultValue = "pended") @Pattern(regexp = "pended") String status) {
    return ResponseEntity.ok(queue.pended());
  }
}
