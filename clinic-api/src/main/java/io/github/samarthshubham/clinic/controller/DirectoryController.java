package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.DirectoryResponse;
import io.github.samarthshubham.clinic.service.DirectoryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/directory")
public class DirectoryController {

  private final DirectoryService directory;

  public DirectoryController(DirectoryService directory) {
    this.directory = directory;
  }

  @PreAuthorize("hasAuthority('order:write')")
  @GetMapping
  public DirectoryResponse search(
      @RequestParam @NotBlank String patientId,
      @RequestParam(defaultValue = "") @Size(max = 100) String specialty,
      @RequestParam(defaultValue = "") @Size(max = 100) String city) {
    return directory.search(patientId, specialty.strip(), city.strip());
  }
}
