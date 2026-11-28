package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.PriorAuthRequest;
import io.github.samarthshubham.clinic.dto.PriorAuthResponse;
import io.github.samarthshubham.clinic.service.pas.PriorAuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prior-auths")
public class PriorAuthController {

  private final PriorAuthService priorAuths;

  public PriorAuthController(PriorAuthService priorAuths) {
    this.priorAuths = priorAuths;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('priorauth:write')")
  public PriorAuthResponse submit(@Valid @RequestBody PriorAuthRequest request) {
    return priorAuths.submit(request);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('priorauth:read')")
  public List<PriorAuthResponse> list(@RequestParam @NotBlank String patientId) {
    return priorAuths.list(patientId);
  }
}
