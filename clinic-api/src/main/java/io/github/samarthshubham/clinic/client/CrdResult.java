package io.github.samarthshubham.clinic.client;

import io.github.samarthshubham.clinic.dto.CrdCard;
import java.util.List;
import org.hl7.fhir.r4.model.Extension;

public record CrdResult(List<Extension> coverageInformation, List<CrdCard> cards) {

  public static final CrdCard COVERAGE_UNKNOWN =
      new CrdCard(
          "Coverage unknown",
          "warning",
          "The payer could not be reached, so coverage and prior authorization requirements are"
              + " unknown. Check them with the payer before the order is performed.",
          "clinic-api");

  public CrdResult {
    coverageInformation = List.copyOf(coverageInformation);
    cards = List.copyOf(cards);
  }

  public static CrdResult none() {
    return new CrdResult(List.of(), List.of());
  }

  public static CrdResult coverageUnknown() {
    return new CrdResult(List.of(), List.of(COVERAGE_UNKNOWN));
  }
}
