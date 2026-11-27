package io.github.samarthshubham.payer.service;

import static io.github.samarthshubham.payer.service.ClaimSubmitService.DECISION_DEADLINE;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.REVIEW_ACTION;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.REVIEW_ACTION_CODE;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.X12_REVIEW_ACTION_SYSTEM;
import static io.github.samarthshubham.payer.service.ClaimSubmitService.sameReference;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.github.samarthshubham.payer.dto.PageResponse;
import io.github.samarthshubham.payer.dto.PriorAuthQueueItem;
import io.github.samarthshubham.payer.mapper.PageMapper;
import io.github.samarthshubham.payer.mapper.PriorAuthQueueMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.ClaimResponse.AdjudicationComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Service;

@Service
public class PriorAuthQueueService {

  private final IGenericClient fhirClient;
  private final SlaClock slaClock;
  private final PriorAuthQueueMapper mapper;

  public PriorAuthQueueService(
      IGenericClient fhirClient, SlaClock slaClock, PriorAuthQueueMapper mapper) {
    this.fhirClient = fhirClient;
    this.slaClock = slaClock;
    this.mapper = mapper;
  }

  public PageResponse<PriorAuthQueueItem> pended() {
    Bundle page =
        fhirClient
            .search()
            .forResource(Claim.class)
            .where(Claim.USE.exactly().code(Claim.Use.PREAUTHORIZATION.toCode()))
            .and(Claim.STATUS.exactly().code(Claim.ClaimStatus.ACTIVE.toCode()))
            .include(Claim.INCLUDE_PATIENT)
            .revInclude(ClaimResponse.INCLUDE_REQUEST)
            .count(100)
            .returnBundle(Bundle.class)
            .execute();
    List<PriorAuthQueueItem> items = new ArrayList<>();
    Instant now = slaClock.now();
    while (true) {
      items.addAll(itemsOnPage(page, now));
      if (page.getLink(Bundle.LINK_NEXT) == null) {
        return new PageResponse<>(List.copyOf(items), items.size());
      }
      page = fhirClient.loadPage().next(page).execute();
    }
  }

  private List<PriorAuthQueueItem> itemsOnPage(Bundle page, Instant now) {
    Map<String, Patient> patients =
        PageMapper.resources(page, Patient.class).stream()
            .collect(
                Collectors.toMap(Patient::getIdPart, Function.identity(), (first, next) -> first));
    Map<String, List<ClaimResponse>> responses =
        PageMapper.resources(page, ClaimResponse.class).stream()
            .filter(response -> response.getUse() == ClaimResponse.Use.PREAUTHORIZATION)
            .filter(ClaimResponse::hasRequest)
            .filter(ClaimResponse::hasCreated)
            .filter(response -> !response.getRequest().getReferenceElement().isAbsolute())
            .collect(
                Collectors.groupingBy(
                    response ->
                        response.getRequest().getReferenceElement().toVersionless().getValue()));

    List<PriorAuthQueueItem> items = new ArrayList<>();
    for (Claim claim : PageMapper.resources(page, Claim.class)) {
      if (!claim.hasPatient() || claim.getPatient().getReferenceElement().isAbsolute()) {
        continue;
      }
      Patient patient = patients.get(claim.getPatient().getReferenceElement().getIdPart());
      if (patient == null) {
        continue;
      }
      responses.getOrDefault(ClaimSubmitService.referenceOf(claim), List.of()).stream()
          .filter(response -> sameReference(response.getPatient(), claim.getPatient()))
          .max(ClaimInquiryService.CURRENT_DECISION_ORDER)
          .filter(PriorAuthQueueService::isPended)
          .ifPresent(response -> items.add(toItem(claim, response, patient, now)));
    }
    return items;
  }

  private PriorAuthQueueItem toItem(
      Claim claim, ClaimResponse response, Patient patient, Instant now) {
    boolean expedited = PasRequest.expedited(claim);
    Extension stored = claim.getExtensionByUrl(DECISION_DEADLINE);
    Instant deadline =
        stored != null && stored.getValue() instanceof DateTimeType time && time.hasValue()
            ? time.getValue().toInstant()
            : slaClock.decisionDeadline(
                slaClock.received(claim.hasCreated() ? claim.getCreated().toInstant() : null, now),
                expedited);
    return mapper.toItem(
        claim, response, patient, expedited, deadline, slaClock.remaining(deadline, now));
  }

  private static boolean isPended(ClaimResponse response) {
    if (response.getStatus() != ClaimResponse.ClaimResponseStatus.ACTIVE) {
      return false;
    }
    return Stream.of(
            response.getAdjudication().stream(),
            response.getItem().stream().flatMap(item -> item.getAdjudication().stream()),
            response.getAddItem().stream().flatMap(item -> item.getAdjudication().stream()))
        .flatMap(Function.identity())
        .anyMatch(PriorAuthQueueService::hasPendingAction);
  }

  private static boolean hasPendingAction(AdjudicationComponent adjudication) {
    Extension action = adjudication.getExtensionByUrl(REVIEW_ACTION);
    Extension code = action == null ? null : action.getExtensionByUrl(REVIEW_ACTION_CODE);
    return code != null
        && code.getValue() instanceof CodeableConcept concept
        && concept.hasCoding(X12_REVIEW_ACTION_SYSTEM, "A4");
  }
}
