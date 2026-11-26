package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import io.github.samarthshubham.clinic.client.PayerApiException;
import io.github.samarthshubham.clinic.config.PayerApiProperties;
import io.github.samarthshubham.clinic.dto.DirectoryProvider;
import io.github.samarthshubham.clinic.dto.DirectoryResponse;
import io.github.samarthshubham.clinic.mapper.DirectoryMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.InsurancePlan;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class DirectoryService {

  private static final int PAGE_SIZE = 50;
  private static final List<String> INCLUDES =
      List.of(
          "PractitionerRole:practitioner",
          "PractitionerRole:location",
          "PractitionerRole:organization");

  private final CoverageService coverageService;
  private final PayerApiProperties properties;
  private final RestClient payerApiRestClient;
  private final FhirContext context;

  public DirectoryService(
      CoverageService coverageService,
      PayerApiProperties properties,
      RestClient payerApiRestClient,
      FhirContext context) {
    this.coverageService = coverageService;
    this.properties = properties;
    this.payerApiRestClient = payerApiRestClient;
    this.context = context;
  }

  public DirectoryResponse search(String patientId, String specialty, String city) {
    var coverage = coverageService.findActiveCoverage(patientId);
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (coverage == null
        || !properties.payerId().equals(coverage.payerId())
        || coverage.planName() == null
        || coverage.planName().isBlank()
        || (coverage.periodStart() != null && coverage.periodStart().isAfter(today))
        || (coverage.periodEnd() != null && coverage.periodEnd().isBefore(today))) {
      return new DirectoryResponse(null, List.of());
    }

    Bundle bundle = planSearch(coverage.planName());
    Date now = new Date();
    List<InsurancePlan> plans =
        bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(InsurancePlan.class::isInstance)
            .map(InsurancePlan.class::cast)
            .filter(plan -> plan.getStatus() == Enumerations.PublicationStatus.ACTIVE)
            .filter(plan -> coverage.planName().equals(plan.getName()))
            .filter(plan -> !plan.getPeriod().hasStart() || !plan.getPeriod().getStart().after(now))
            .filter(plan -> !plan.getPeriod().hasEnd() || !plan.getPeriod().getEnd().before(now))
            .toList();
    if (plans.size() != 1 || bundle.getLink("next") != null) {
      return new DirectoryResponse(null, List.of());
    }
    List<String> networks =
        plans.getFirst().getNetwork().stream()
            .map(Reference::getReferenceElement)
            .filter(id -> "Organization".equals(id.getResourceType()))
            .map(id -> id.getIdPart())
            .filter(id -> id != null && id.matches("[A-Za-z0-9.-]{1,64}"))
            .map(id -> "Organization/" + id)
            .distinct()
            .toList();
    if (networks.isEmpty()) {
      return new DirectoryResponse(null, List.of());
    }
    return new DirectoryResponse(
        coverage.planName(), providers(String.join(",", networks), specialty, city));
  }

  private Bundle planSearch(String name) {
    return parse(
        () ->
            payerApiRestClient
                .get()
                .uri(
                    builder ->
                        builder
                            .path("/fhir/InsurancePlan")
                            .queryParam("name:exact", "{plan}")
                            .build(Map.of("plan", escapeSearch(name))))
                .accept(MediaType.parseMediaType("application/fhir+json"))
                .retrieve()
                .body(String.class));
  }

  private List<DirectoryProvider> providers(String network, String specialty, String city) {
    List<DirectoryProvider> rows = new ArrayList<>();
    int offset = 0;
    Bundle page;
    do {
      page = roleSearch(network, offset);
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

  private Bundle roleSearch(String network, int offset) {
    return parse(
        () ->
            payerApiRestClient
                .get()
                .uri(
                    builder ->
                        builder
                            .path("/fhir/PractitionerRole")
                            .queryParam("_include", INCLUDES)
                            .queryParam("_count", PAGE_SIZE)
                            .queryParam("_offset", offset)
                            .queryParam("network", "{network}")
                            .build(Map.of("network", network)))
                .accept(MediaType.parseMediaType("application/fhir+json"))
                .retrieve()
                .body(String.class));
  }

  private Bundle parse(java.util.function.Supplier<String> response) {
    try {
      String body = response.get();
      if (body == null || body.isBlank()) {
        throw new PayerApiException("The payer directory returned no data");
      }
      return context.newJsonParser().parseResource(Bundle.class, body);
    } catch (RestClientException | DataFormatException e) {
      throw new PayerApiException("The payer directory is unavailable");
    }
  }

  private static String escapeSearch(String value) {
    return value.replace("\\", "\\\\").replace(",", "\\,").replace("|", "\\|").replace("$", "\\$");
  }
}
