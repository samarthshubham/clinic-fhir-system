package io.github.samarthshubham.payer.service;

import io.github.samarthshubham.payer.dto.DirectoryProvider;
import io.github.samarthshubham.payer.interop.PlanNetDirectory;
import io.github.samarthshubham.payer.mapper.DirectoryMapper;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class DirectoryService {

  private final PlanNetDirectory directory;

  public DirectoryService(PlanNetDirectory directory) {
    this.directory = directory;
  }

  public List<DirectoryProvider> search(String specialty, String city) {
    List<DirectoryProvider> rows = new ArrayList<>();
    int offset = 0;
    Bundle page;
    do {
      page = directory.practitionerRoles(offset);
      rows.addAll(DirectoryMapper.providers(page, specialty, city));
      offset = nextOffset(page, offset);
    } while (offset >= 0);
    return rows.stream().distinct().toList();
  }

  private static int nextOffset(Bundle page, int currentOffset) {
    var next = page.getLink(Bundle.LINK_NEXT);
    if (next == null || !next.hasUrl()) {
      return -1;
    }
    try {
      var values =
          UriComponentsBuilder.fromUriString(next.getUrl()).build().getQueryParams().get("_offset");
      if (values == null || values.size() != 1) {
        return -1;
      }
      int offset = Integer.parseInt(values.getFirst());
      return offset > currentOffset ? offset : -1;
    } catch (IllegalArgumentException e) {
      return -1;
    }
  }
}
