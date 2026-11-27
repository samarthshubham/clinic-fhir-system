package io.github.samarthshubham.payer.controller;

import io.github.samarthshubham.payer.dto.DirectoryProvider;
import io.github.samarthshubham.payer.service.DirectoryService;
import jakarta.validation.constraints.Size;
import java.util.List;
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

  @PreAuthorize("hasAuthority('member:read')")
  @GetMapping
  public List<DirectoryProvider> search(
      @RequestParam(defaultValue = "") @Size(max = 100) String specialty,
      @RequestParam(defaultValue = "") @Size(max = 100) String city) {
    return directory.search(specialty.strip(), city.strip());
  }
}
