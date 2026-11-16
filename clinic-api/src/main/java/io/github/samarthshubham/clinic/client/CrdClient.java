package io.github.samarthshubham.clinic.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.samarthshubham.clinic.dto.CrdCard;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class CrdClient {

  public static final String COVERAGE_INFORMATION =
      "http://hl7.org/fhir/us/davinci-crd/StructureDefinition/ext-coverage-information";
  static final String CLINIC_FHIR_BASE = "http://praxis.clinic/fhir";
  private static final String CRD_PROFILES =
      "http://hl7.org/fhir/us/davinci-crd/StructureDefinition/";

  public enum Hook {
    ORDER_SIGN("order-sign", "order-sign-crd", "draftOrders"),
    APPOINTMENT_BOOK("appointment-book", "appointment-book-crd", "appointments");

    private final String name;
    private final String serviceId;
    private final String contextKey;

    Hook(String name, String serviceId, String contextKey) {
      this.name = name;
      this.serviceId = serviceId;
      this.contextKey = contextKey;
    }
  }

  private final RestClient payerApiRestClient;
  private final PayerTokenClient tokenClient;
  private final FhirContext fhirContext;
  private final ObjectMapper objectMapper;

  public CrdClient(
      RestClient payerApiRestClient,
      PayerTokenClient tokenClient,
      FhirContext fhirContext,
      ObjectMapper objectMapper) {
    this.payerApiRestClient = payerApiRestClient;
    this.tokenClient = tokenClient;
    this.fhirContext = fhirContext;
    this.objectMapper = objectMapper;
  }

  public CrdResult orderSign(
      ServiceRequest order, Patient patient, Coverage coverage, Practitioner practitioner) {
    return call(Hook.ORDER_SIGN, order, patient, coverage, practitioner);
  }

  public CrdResult appointmentBook(
      Appointment appointment, Patient patient, Coverage coverage, Practitioner practitioner) {
    return call(Hook.APPOINTMENT_BOOK, appointment, patient, coverage, practitioner);
  }

  private CrdResult call(
      Hook hook,
      DomainResource resource,
      Patient patient,
      Coverage coverage,
      Practitioner practitioner) {
    String hookInstance = UUID.randomUUID().toString();
    DomainResource draft = resource.copy();
    draft.setId("draft-" + hookInstance);
    String draftReference = draft.fhirType() + "/" + draft.getIdPart();
    try {
      JsonNode response =
          post(hook, buildRequest(hook, hookInstance, draft, patient, coverage, practitioner));
      return parseResponse(response, draftReference);
    } catch (RestClientException | PayerApiException | DataFormatException e) {
      log.warn(
          "CRD {} hook {} for {} of Patient/{} failed open: {}",
          hook.name,
          hookInstance,
          draftReference,
          patient.getIdPart(),
          describe(e));
      return CrdResult.coverageUnknown();
    }
  }

  private JsonNode post(Hook hook, ObjectNode request) {
    try {
      JsonNode response =
          payerApiRestClient
              .post()
              .uri("/cds-services/{id}", hook.serviceId)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenClient.accessToken())
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(JsonNode.class);
      if (response == null) {
        throw new PayerApiException("payer-api answered the hook with an empty body");
      }
      return response;
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
        tokenClient.evict();
      }
      throw e;
    }
  }

  ObjectNode buildRequest(
      Hook hook,
      String hookInstance,
      DomainResource draft,
      Patient patient,
      Coverage coverage,
      Practitioner practitioner) {
    IParser parser = fhirContext.newJsonParser();
    ObjectNode request = objectMapper.createObjectNode();
    request.put("hookInstance", hookInstance);
    request.put("hook", hook.name);
    ObjectNode context = request.putObject("context");
    if (practitioner != null) {
      context.put("userId", "Practitioner/" + practitioner.getIdPart());
    }
    context.put("patientId", patient.getIdPart());
    context.set(hook.contextKey, tree(parser, draftBundle(draft, coverage)));
    ObjectNode prefetch = request.putObject("prefetch");
    prefetch.set("patient", tree(parser, prefetchCopy(patient)));
    prefetch.set("coverage", tree(parser, coverageSearchset(coverage)));
    if (practitioner != null) {
      prefetch.set("practitioner", tree(parser, prefetchCopy(practitioner)));
    }
    return request;
  }

  private static Bundle draftBundle(DomainResource draft, Coverage coverage) {
    DomainResource copy = draft.copy();
    Bundle bundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
    switch (copy) {
      case ServiceRequest order -> {
        order.setMeta(new Meta().addProfile(CRD_PROFILES + "profile-servicerequest"));
        order.setStatus(ServiceRequest.ServiceRequestStatus.DRAFT);
        order.getInsurance().clear();
        order.addInsurance(new Reference("Coverage/" + coverage.getIdPart()));
      }
      case Appointment appointment -> {
        bundle.setMeta(new Meta().addProfile(CRD_PROFILES + "profile-bundle-appointment"));
        appointment.setMeta(new Meta().addProfile(CRD_PROFILES + "profile-appointment-no-order"));
        appointment.setStatus(Appointment.AppointmentStatus.PROPOSED);
      }
      default -> throw new IllegalArgumentException("No CRD hook for a draft " + copy.fhirType());
    }
    bundle.addEntry().setFullUrl(fullUrl(copy)).setResource(copy);
    return bundle;
  }

  private static Bundle coverageSearchset(Coverage coverage) {
    Coverage copy = (Coverage) prefetchCopy(coverage);
    copy.setMeta(new Meta().addProfile(CRD_PROFILES + "profile-coverage"));
    Bundle bundle = new Bundle().setType(Bundle.BundleType.SEARCHSET).setTotal(1);
    bundle
        .addEntry()
        .setFullUrl(fullUrl(copy))
        .setResource(copy)
        .getSearch()
        .setMode(Bundle.SearchEntryMode.MATCH);
    return bundle;
  }

  private static Resource prefetchCopy(Resource resource) {
    Resource copy = resource.copy();
    copy.setId(resource.getIdPart());
    Meta meta = new Meta();
    resource.getMeta().getProfile().forEach(profile -> meta.addProfile(profile.getValue()));
    copy.setMeta(meta);
    return copy;
  }

  private static String fullUrl(Resource resource) {
    return CLINIC_FHIR_BASE + "/" + resource.fhirType() + "/" + resource.getIdPart();
  }

  private JsonNode tree(IParser parser, IBaseResource resource) {
    try {
      return objectMapper.readTree(parser.encodeResourceToString(resource));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("HAPI wrote JSON Jackson cannot read", e);
    }
  }

  CrdResult parseResponse(JsonNode response, String draftReference) {
    List<CrdCard> cards = new ArrayList<>();
    for (JsonNode card : response.path("cards")) {
      cards.add(
          new CrdCard(
              text(card, "summary"),
              text(card, "indicator"),
              text(card, "detail"),
              text(card.path("source"), "label")));
    }
    IParser parser = fhirContext.newJsonParser();
    List<Extension> coverageInformation = new ArrayList<>();
    for (JsonNode action : response.path("systemActions")) {
      JsonNode resource = action.path("resource");
      if (!resource.isObject()) {
        continue;
      }
      if (parser.parseResource(resource.toString()) instanceof DomainResource updated
          && draftReference.equals(updated.fhirType() + "/" + updated.getIdPart())) {
        coverageInformation.addAll(updated.getExtensionsByUrl(COVERAGE_INFORMATION));
      }
    }
    return new CrdResult(coverageInformation, cards);
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isTextual() ? value.asText() : null;
  }

  private static String describe(Exception e) {
    if (e instanceof RestClientResponseException response) {
      return "HTTP " + response.getStatusCode().value();
    }
    return e.getClass().getSimpleName();
  }
}
