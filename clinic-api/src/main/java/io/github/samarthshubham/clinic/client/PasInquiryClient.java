package io.github.samarthshubham.clinic.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PasInquiryClient {

  private static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");

  private final RestClient payerApiRestClient;
  private final PayerTokenClient tokenClient;
  private final FhirContext fhirContext;

  public PasInquiryClient(
      RestClient payerApiRestClient, PayerTokenClient tokenClient, FhirContext fhirContext) {
    this.payerApiRestClient = payerApiRestClient;
    this.tokenClient = tokenClient;
    this.fhirContext = fhirContext;
  }

  public Optional<ClaimResponse> inquire(Bundle request) {
    try {
      String body =
          payerApiRestClient
              .post()
              .uri("/fhir/Claim/$inquire")
              .headers(headers -> headers.setBearerAuth(tokenClient.accessToken()))
              .contentType(FHIR_JSON)
              .accept(FHIR_JSON)
              .body(fhirContext.newJsonParser().encodeResourceToString(request))
              .retrieve()
              .body(String.class);
      if (body == null || body.isBlank()) {
        throw new PayerApiException("payer-api returned an empty PAS inquiry body");
      }
      Parameters response = fhirContext.newJsonParser().parseResource(Parameters.class, body);
      var returns =
          response.getParameter().stream()
              .filter(parameter -> "return".equals(parameter.getName()))
              .toList();
      if (returns.isEmpty()) {
        return Optional.empty();
      }
      if (returns.size() != 1
          || !(returns.getFirst().getResource() instanceof Bundle bundle)
          || !bundle.hasEntry()
          || !(bundle.getEntryFirstRep().getResource() instanceof ClaimResponse decision)) {
        throw new PayerApiException("payer-api returned an unexpected PAS inquiry result");
      }
      return Optional.of(decision);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
        tokenClient.evict();
      }
      throw new PayerApiException("PAS inquiry failed with HTTP " + e.getStatusCode().value());
    } catch (RestClientException | DataFormatException e) {
      throw new PayerApiException("payer-api returned no usable PAS inquiry response");
    }
  }
}
