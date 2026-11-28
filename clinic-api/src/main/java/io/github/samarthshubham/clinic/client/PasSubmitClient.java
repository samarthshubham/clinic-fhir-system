package io.github.samarthshubham.clinic.client;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import io.github.samarthshubham.clinic.config.PayerApiConfig;
import io.github.samarthshubham.clinic.config.PayerApiProperties;
import io.github.samarthshubham.clinic.exception.PasRequestException;
import io.github.samarthshubham.clinic.exception.PasSubmissionUnavailableException;
import java.time.Duration;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class PasSubmitClient {

  private static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");
  private final RestClient restClient;
  private final PayerTokenClient tokens;
  private final FhirContext fhirContext;

  @Autowired
  public PasSubmitClient(
      RestClient.Builder builder,
      PayerApiProperties payer,
      @Value("${clinic.payer.pas-submit-timeout}") Duration timeout,
      PayerTokenClient tokens,
      FhirContext fhirContext) {
    this(PayerApiConfig.payerApiRestClient(builder, payer.baseUrl(), timeout), tokens, fhirContext);
  }

  PasSubmitClient(RestClient restClient, PayerTokenClient tokens, FhirContext fhirContext) {
    this.restClient = restClient;
    this.tokens = tokens;
    this.fhirContext = fhirContext;
  }

  public ClaimResponse submit(Bundle submission) {
    try {
      String body =
          restClient
              .post()
              .uri("/fhir/Claim/$submit")
              .headers(headers -> headers.setBearerAuth(tokens.accessToken()))
              .contentType(FHIR_JSON)
              .accept(FHIR_JSON)
              .body(fhirContext.newJsonParser().encodeResourceToString(submission))
              .retrieve()
              .body(String.class);
      if (body == null || body.isBlank()) {
        throw unavailable();
      }
      Bundle result = fhirContext.newJsonParser().parseResource(Bundle.class, body);
      if (!result.hasEntry()
          || !(result.getEntryFirstRep().getResource() instanceof ClaimResponse decision)
          || !submission.getIdentifier().equalsDeep(result.getIdentifier())
          || !submission
              .getEntryFirstRep()
              .getFullUrl()
              .equals(decision.getRequest().getReference())
          || !decision.hasOutcome()) {
        throw unavailable();
      }
      if (decision.getOutcome() == ClaimResponse.RemittanceOutcome.ERROR) {
        throw new PasRequestException(
            "The payer could not process the prior authorization request.");
      }
      return decision;
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
        tokens.evict();
      }
      if (e.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)
          || e.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_ENTITY)) {
        throw new PasRequestException(
            "The payer rejected the request. Check the order and billing codes.");
      }
      throw unavailable();
    } catch (RestClientException | DataFormatException | PayerApiException e) {
      throw unavailable();
    }
  }

  private static PasSubmissionUnavailableException unavailable() {
    return new PasSubmissionUnavailableException(
        "A response to the prior authorization request could not be confirmed. Try again.");
  }
}
