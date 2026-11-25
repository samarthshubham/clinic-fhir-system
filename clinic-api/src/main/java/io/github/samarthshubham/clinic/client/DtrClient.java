package io.github.samarthshubham.clinic.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class DtrClient {

  static final String QUESTIONNAIRE_PACKAGE = "/fhir/Questionnaire/$questionnaire-package";
  static final String INPUT_PROFILE =
      "http://hl7.org/fhir/us/davinci-dtr/StructureDefinition/dtr-qpackage-input-parameters";
  static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");

  private final RestClient payerApiRestClient;
  private final PayerTokenClient tokenClient;
  private final FhirContext fhirContext;

  public DtrClient(
      RestClient payerApiRestClient, PayerTokenClient tokenClient, FhirContext fhirContext) {
    this.payerApiRestClient = payerApiRestClient;
    this.tokenClient = tokenClient;
    this.fhirContext = fhirContext;
  }

  /**
   * Returns the payer's first questionnaire package for the order, or empty when the payer needs no
   * documentation for it. Throws {@link PayerApiException} when payer-api gives no usable answer.
   */
  public Optional<DtrPackage> questionnairePackage(ServiceRequest order, Coverage coverage) {
    String request = fhirContext.newJsonParser().encodeResourceToString(input(order, coverage));
    String response = post(request, order.getIdPart());
    Parameters output;
    try {
      output = fhirContext.newJsonParser().parseResource(Parameters.class, response);
    } catch (DataFormatException e) {
      log.warn(
          "DTR $questionnaire-package for ServiceRequest/{} returned no Parameters",
          order.getIdPart());
      throw new PayerApiException("payer-api answered $questionnaire-package with invalid FHIR");
    }
    return output.getParameter().stream()
        .filter(parameter -> "packagebundle".equals(parameter.getName()))
        .map(Parameters.ParametersParameterComponent::getResource)
        .filter(Bundle.class::isInstance)
        .map(Bundle.class::cast)
        .findFirst()
        .map(DtrClient::toPackage);
  }

  private String post(String request, String orderId) {
    try {
      String response =
          payerApiRestClient
              .post()
              .uri(QUESTIONNAIRE_PACKAGE)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenClient.accessToken())
              .contentType(FHIR_JSON)
              .accept(FHIR_JSON)
              .body(request)
              .retrieve()
              .body(String.class);
      if (response == null || response.isBlank()) {
        throw new PayerApiException("payer-api answered $questionnaire-package with no body");
      }
      return response;
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
        tokenClient.evict();
      }
      log.warn(
          "DTR $questionnaire-package for ServiceRequest/{} failed: HTTP {}",
          orderId,
          e.getStatusCode().value());
      throw new PayerApiException(
          "payer-api answered $questionnaire-package with HTTP " + e.getStatusCode().value());
    } catch (RestClientException e) {
      log.warn(
          "DTR $questionnaire-package for ServiceRequest/{} failed: {}",
          orderId,
          e.getClass().getSimpleName());
      throw new PayerApiException("payer-api could not be reached");
    }
  }

  static Parameters input(ServiceRequest order, Coverage coverage) {
    Parameters input = new Parameters();
    input.setMeta(new Meta().addProfile(INPUT_PROFILE));
    Coverage coverageCopy = (Coverage) CrdClient.prefetchCopy(coverage);
    coverageCopy.setMeta(new Meta().addProfile(CrdClient.CRD_PROFILES + "profile-coverage"));
    input.addParameter().setName("coverage").setResource(coverageCopy);
    ServiceRequest orderCopy = (ServiceRequest) CrdClient.prefetchCopy(order);
    orderCopy.setMeta(new Meta().addProfile(CrdClient.CRD_PROFILES + "profile-servicerequest"));
    orderCopy.getExtension().removeIf(e -> e.getUrl().startsWith(CrdClient.CLINIC_FHIR_BASE));
    input.addParameter().setName("order").setResource(orderCopy);
    return input;
  }

  private static DtrPackage toPackage(Bundle bundle) {
    List<Resource> resources =
        bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).toList();
    Questionnaire questionnaire =
        first(resources, Questionnaire.class)
            .orElseThrow(
                () ->
                    new PayerApiException(
                        "payer-api sent a questionnaire package without a Questionnaire"));
    return new DtrPackage(
        questionnaire,
        first(resources, QuestionnaireResponse.class).orElse(null),
        resources.stream().filter(ValueSet.class::isInstance).map(ValueSet.class::cast).toList());
  }

  private static <T extends Resource> Optional<T> first(List<Resource> resources, Class<T> type) {
    return resources.stream().filter(type::isInstance).map(type::cast).findFirst();
  }
}
