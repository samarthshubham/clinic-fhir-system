package io.github.samarthshubham.payer.cdshooks;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.samarthshubham.payer.config.CrdProperties;
import io.github.samarthshubham.payer.mapper.MemberMapper;
import io.github.samarthshubham.payer.mapper.PageMapper;
import io.github.samarthshubham.payer.service.AuditEventService;
import io.github.samarthshubham.payer.service.MemberCoverageCheck;
import io.github.samarthshubham.payer.service.MemberCoverageService;
import io.github.samarthshubham.payer.service.OrderPriority;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CrdHookService {

  static final String LATENCY_METRIC = "crd.hook";

  private final FhirContext fhirContext;
  private final ObjectMapper objectMapper;
  private final PrefetchResolver prefetchResolver;
  private final MemberCoverageService memberCoverageService;
  private final CoverageDecisions coverageDecisions;
  private final AuditEventService auditEventService;
  private final CrdProperties crdProperties;
  private final MeterRegistry meterRegistry;
  private final Clock clock;

  public CrdHookService(
      FhirContext fhirContext,
      ObjectMapper objectMapper,
      PrefetchResolver prefetchResolver,
      MemberCoverageService memberCoverageService,
      CoverageDecisions coverageDecisions,
      AuditEventService auditEventService,
      CrdProperties crdProperties,
      MeterRegistry meterRegistry,
      Clock clock) {
    this.fhirContext = fhirContext;
    this.objectMapper = objectMapper;
    this.prefetchResolver = prefetchResolver;
    this.memberCoverageService = memberCoverageService;
    this.coverageDecisions = coverageDecisions;
    this.auditEventService = auditEventService;
    this.crdProperties = crdProperties;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
  }

  public CdsDiscovery discovery() {
    return CdsDiscovery.of(CrdHook.values());
  }

  public JsonNode call(String serviceId, String body, String clientId) {
    Timer.Sample sample = Timer.start(meterRegistry);
    CrdHook hook = CrdHook.byServiceId(serviceId).orElse(null);
    String hookInstance = null;
    List<String> payerReferences = new ArrayList<>();
    HttpStatus status = HttpStatus.OK;
    try {
      if (hook == null) {
        throw new CdsHookException(HttpStatus.NOT_FOUND, "No CDS service named " + serviceId);
      }
      CdsHookRequest request = CdsHookRequest.from(readJson(body));
      hookInstance = request.hookInstance();
      return answer(hook, request, payerReferences);
    } catch (RuntimeException e) {
      status = CdsHooksExceptionHandler.statusOf(e);
      throw e;
    } finally {
      sample.stop(
          meterRegistry.timer(
              LATENCY_METRIC,
              "service",
              hook == null ? "unknown" : hook.serviceId(),
              "status",
              String.valueOf(status.value())));
      auditEventService.record(
          new AuditEventService.InteropAudit(
              "cds-services/" + (hook == null ? "unknown" : hook.serviceId()),
              hookInstance,
              clientId,
              status.value(),
              payerReferences));
    }
  }

  private JsonNode answer(CrdHook hook, CdsHookRequest request, List<String> payerReferences) {
    if (!hook.hook().equals(request.hook())) {
      throw CdsHookRequest.badRequest("This service answers " + hook.hook() + " hooks");
    }
    if (request.hookInstance() == null) {
      throw CdsHookRequest.badRequest("The hook request has no hookInstance");
    }
    if (request.contextValue("patientId") == null) {
      throw CdsHookRequest.badRequest("The hook context has no patientId");
    }
    IParser parser = fhirContext.newJsonParser();
    List<? extends DomainResource> targets =
        switch (hook) {
          case APPOINTMENT_BOOK ->
              contextBundle(request.appointments(), "appointments", Appointment.class, parser);
          case ENCOUNTER_START -> List.of(encounter(request, parser));
          case ORDER_DISPATCH -> dispatchedOrders(request, parser);
          case ORDER_SIGN, ORDER_SELECT -> draftOrders(hook, request, parser);
        };
    Coverage coverage = coverage(request, parser);
    LocalDate today = LocalDate.now(clock);
    MemberCoverageCheck check = memberCoverageService.check(memberId(coverage), today);
    if (check.payerCoverageId() != null) {
      payerReferences.add("Coverage/" + check.payerCoverageId());
    }

    ObjectNode response = objectMapper.createObjectNode();
    ArrayNode cards = response.putArray("cards");
    ArrayNode systemActions = response.putArray("systemActions");
    String coverageReference = "Coverage/" + coverage.getIdElement().getIdPart();
    for (DomainResource target : targets) {
      CoverageDecision decision = decide(hook, target, check);
      String targetReference = target.fhirType() + "/" + target.getIdElement().getIdPart();
      String assertionId = CoverageDecisions.stableId(request.hookInstance(), targetReference);
      DomainResource updated = target.copy();
      updated
          .getExtension()
          .removeIf(e -> CoverageDecisions.COVERAGE_INFORMATION.equals(e.getUrl()));
      updated.addExtension(
          coverageDecisions.coverageInformation(decision, coverageReference, today, assertionId));
      systemActions.add(systemAction(updated, parser));
      cards.add(card(decision, request.hookInstance(), targetReference, today));
    }
    return response;
  }

  private CoverageDecision decide(CrdHook hook, DomainResource target, MemberCoverageCheck check) {
    return switch (target) {
      case ServiceRequest order -> coverageDecisions.decide(hook, order, check);
      case Appointment appointment ->
          coverageDecisions.decide(
              hook, appointment.getServiceTypeFirstRep(), OrderPriority.ROUTINE, check);
      case Encounter encounter ->
          coverageDecisions.decide(hook, encounter.getTypeFirstRep(), OrderPriority.ROUTINE, check);
      default -> throw new IllegalStateException("No coverage decision for " + target.fhirType());
    };
  }

  private List<ServiceRequest> draftOrders(CrdHook hook, CdsHookRequest request, IParser parser) {
    List<ServiceRequest> orders =
        contextBundle(request.draftOrders(), "draftOrders", ServiceRequest.class, parser);
    if (hook.primary()) {
      return orders;
    }
    List<String> selections = request.selections();
    if (selections.isEmpty()) {
      throw CdsHookRequest.badRequest("The order-select context has no selections");
    }
    return orders.stream()
        .filter(order -> selections.contains("ServiceRequest/" + order.getIdElement().getIdPart()))
        .toList();
  }

  private List<ServiceRequest> dispatchedOrders(CdsHookRequest request, IParser parser) {
    List<String> dispatched = request.contextReferences("dispatchedOrders");
    if (dispatched.isEmpty()) {
      throw CdsHookRequest.badRequest("The order-dispatch context has no dispatchedOrders");
    }
    List<ServiceRequest> orders =
        switch (prefetchResolver.resolve(request, CrdHook.DISPATCHED_ORDERS_PREFETCH, parser)) {
          case Bundle bundle -> PageMapper.resources(bundle, ServiceRequest.class);
          case ServiceRequest order -> List.of(order);
          default ->
              throw CdsHookRequest.badRequest(
                  "Prefetch 'dispatchedOrders' is not a ServiceRequest Bundle");
        };
    return orders.stream()
        .filter(order -> order.getIdElement().hasIdPart())
        .filter(order -> dispatched.contains("ServiceRequest/" + order.getIdElement().getIdPart()))
        .toList();
  }

  private Encounter encounter(CdsHookRequest request, IParser parser) {
    if (!(prefetchResolver.resolve(request, CrdHook.ENCOUNTER_PREFETCH, parser)
            instanceof Encounter encounter)
        || !encounter.getIdElement().hasIdPart()) {
      throw CdsHookRequest.badRequest("Prefetch 'encounter' is not an Encounter with an id");
    }
    if (!encounter.getIdElement().getIdPart().equals(request.contextValue("encounterId"))) {
      throw CdsHookRequest.badRequest("Prefetch 'encounter' is not the context encounterId");
    }
    return encounter;
  }

  private static <T extends DomainResource> List<T> contextBundle(
      JsonNode node, String name, Class<T> type, IParser parser) {
    if (!node.isObject()) {
      throw CdsHookRequest.badRequest("The hook context has no " + name + " Bundle");
    }
    Bundle bundle;
    try {
      bundle = parser.parseResource(Bundle.class, node.toString());
    } catch (DataFormatException e) {
      throw CdsHookRequest.badRequest("context." + name + " is not a FHIR Bundle");
    }
    List<T> resources = PageMapper.resources(bundle, type);
    if (resources.stream().anyMatch(resource -> !resource.getIdElement().hasIdPart())) {
      throw CdsHookRequest.badRequest("Every resource in context." + name + " needs an id");
    }
    return resources;
  }

  private Coverage coverage(CdsHookRequest request, IParser parser) {
    IBaseResource prefetched = prefetchResolver.resolve(request, CrdHook.COVERAGE_PREFETCH, parser);
    List<Coverage> coverages =
        switch (prefetched) {
          case Bundle bundle -> PageMapper.resources(bundle, Coverage.class);
          case Coverage coverage -> List.of(coverage);
          default ->
              throw CdsHookRequest.badRequest("Prefetch 'coverage' is not a Coverage Bundle");
        };
    if (coverages.isEmpty()) {
      throw CdsHookRequest.badRequest("The hook request has no active Coverage");
    }
    List<Coverage> ours = coverages.stream().filter(this::namesOurPayer).toList();
    if (ours.isEmpty()) {
      ours = coverages.stream().filter(coverage -> payerIdentifiers(coverage).isEmpty()).toList();
    }
    if (ours.isEmpty()) {
      throw CdsHookRequest.badRequest(
          "No Coverage in the hook request is with " + crdProperties.payerName());
    }
    if (ours.size() > 1) {
      throw CdsHookRequest.badRequest(
          "The hook request has more than one active Coverage with " + crdProperties.payerName());
    }
    Coverage coverage = ours.get(0);
    if (!coverage.getIdElement().hasIdPart()) {
      throw CdsHookRequest.badRequest("The Coverage needs an id");
    }
    return coverage;
  }

  private boolean namesOurPayer(Coverage coverage) {
    return payerIdentifiers(coverage).stream()
        .anyMatch(id -> crdProperties.isOurPayer(id.getSystem(), id.getValue()));
  }

  private static List<Identifier> payerIdentifiers(Coverage coverage) {
    return coverage.getPayor().stream()
        .filter(Reference::hasIdentifier)
        .map(Reference::getIdentifier)
        .toList();
  }

  private static String memberId(Coverage coverage) {
    return coverage.getIdentifier().stream()
        .filter(identifier -> MemberMapper.MEMBER_ID_SYSTEM.equals(identifier.getSystem()))
        .map(Identifier::getValue)
        .findFirst()
        .orElse(coverage.getSubscriberId());
  }

  private ObjectNode systemAction(DomainResource resource, IParser parser) {
    ObjectNode action = objectMapper.createObjectNode();
    action.put("type", "update");
    action.put("description", "Add " + crdProperties.payerName() + " coverage information");
    action.set("resource", toJson(parser.encodeResourceToString(resource)));
    return action;
  }

  private ObjectNode card(
      CoverageDecision decision, String hookInstance, String orderReference, LocalDate today) {
    ObjectNode card = objectMapper.createObjectNode();
    card.put("uuid", CoverageDecisions.stableId(hookInstance, "card|" + orderReference));
    card.put("summary", decision.summary());
    card.put("indicator", decision.indicator());
    card.put("detail", decision.detail(crdProperties.payerName(), today));
    ObjectNode source = card.putObject("source");
    source.put("label", crdProperties.payerName());
    ObjectNode topic = source.putObject("topic");
    topic.put("system", CoverageDecisions.CARD_TYPE_SYSTEM);
    topic.put("code", "coverage-info");
    topic.put("display", "Coverage Information");
    card.putObject("extension")
        .putArray("davinci-crd.associated-resource")
        .addObject()
        .put("reference", orderReference);
    return card;
  }

  private JsonNode readJson(String body) {
    if (body == null || body.isBlank()) {
      throw CdsHookRequest.badRequest("The hook request has no body");
    }
    try {
      return objectMapper.readTree(body);
    } catch (JsonProcessingException e) {
      throw CdsHookRequest.badRequest("The hook request is not valid JSON");
    }
  }

  private JsonNode toJson(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("HAPI wrote invalid JSON", e);
    }
  }
}
