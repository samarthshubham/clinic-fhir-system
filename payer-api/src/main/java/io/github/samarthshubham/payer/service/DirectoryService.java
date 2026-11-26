package io.github.samarthshubham.payer.service;

import io.github.samarthshubham.payer.dto.DirectoryProvider;
import io.github.samarthshubham.payer.interop.PlanNetDirectory;
import io.github.samarthshubham.payer.mapper.DirectoryMapper;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Service;

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
      offset +=
          (int)
              page.getEntry().stream()
                  .filter(
                      entry ->
                          !entry.getSearch().hasMode()
                              || entry.getSearch().getMode() == Bundle.SearchEntryMode.MATCH)
                  .count();
    } while (page.getLink("next") != null);
    return rows.stream().distinct().toList();
  }
}
