package io.github.samarthshubham.payer.interop;

import static io.github.samarthshubham.payer.support.GoldenFhir.assertMatchesGolden;
import static io.github.samarthshubham.payer.support.GoldenFhir.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceVersionConflictException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import io.github.samarthshubham.payer.service.ClaimSubmitService;
import io.github.samarthshubham.payer.service.TokenService;
import io.github.samarthshubham.payer.support.FhirClientStub;
import io.github.samarthshubham.payer.support.TestAuth;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.convention.TestBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClaimSubmitInteropTest {

  private static final Instant NOW = Instant.parse("2026-08-20T15:15:05Z");
  private static FhirClientStub payerHapi = new FhirClientStub();

  @TestBean private IGenericClient fhirClient;

  @TestBean private Clock clock;

  @LocalServerPort private int port;

  @Autowired private TokenService tokenService;

  private IGenericClient clinicApi;

  static IGenericClient fhirClient() {
    return payerHapi.client();
  }

  static Clock clock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }

  @BeforeEach
  void freshPayerHapi() {
    payerHapi.reset();
    clinicApi =
        withToken(
            tokenService
                .issueSystemToken(TestAuth.CLIENT_ID, TestAuth.CLIENT_SCOPES)
                .accessToken());
  }

  @ParameterizedTest(name = "{0} gets {3}")
  @CsvSource({
    "request-mri.json, PHP-10005, member-sandra, response-denied.json",
    "request-cgm.json, PHP-10002, member-ramon, response-approved.json",
    "request-polysomnography.json, PHP-10003, member-gerald, response-pended.json",
  })
  void goldenRequestGetsTheGoldenResponse(
      String request, String memberId, String memberPatient, String response) {
    givenActiveMember(memberPatient);

    Bundle answer = submit(request);

    assertMatchesGolden(answer, "pas/" + response);
    assertThat(payerHapi.lastSearchCriteria(Coverage.class))
        .contains(
            "identifier=http://praxishealthplan.example/fhir/identifier/member-id|" + memberId);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void inquiryFindsTheCurrentDecisionForTheSubmittedClaim(boolean rawBundle) throws Exception {
    givenActiveMember("member-gerald");
    Bundle submitted = submit("request-polysomnography.json");
    assertThat(resource(submitted, ClaimResponse.class).getDisposition())
        .isEqualTo("Pended for clinical review");
    Bundle transaction = payerHapi.transactions().getFirst();
    Claim claim = resource(transaction, Claim.class);
    claim.setId("1");
    ClaimResponse pended = resource(transaction, ClaimResponse.class);
    pended.setId("2");
    pended.setRequest(new Reference("Claim/1"));
    pended.setCreatedElement(new DateTimeType("2026-08-20T15:14:05Z"));
    ClaimResponse approved =
        resource(
            (Bundle)
                fixture(Parameters.class, "pas/inquire-response.json")
                    .getParameterFirstRep()
                    .getResource(),
            ClaimResponse.class);
    approved.setRequest(new Reference("Claim/1"));
    approved.setPatient(claim.getPatient().copy());
    Patient patient = new Patient();
    patient
        .addIdentifier()
        .setSystem("http://praxishealthplan.example/fhir/identifier/member-id")
        .setValue("PHP-10003");
    payerHapi.givenStored(patient, "member-gerald");
    payerHapi.givenSearchResult(Claim.class, FhirClientStub.bundleOf(claim));
    payerHapi.givenSearchResult(ClaimResponse.class, FhirClientStub.bundleOf(pended, approved));

    Bundle inquiry = fixture(Bundle.class, "pas/inquire-request.json");
    Parameters input = new Parameters();
    input.addParameter().setName("resource").setResource(inquiry);
    FhirContext context = FhirContext.forR4Cached();
    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/fhir/Claim/$inquire"))
                    .header("Content-Type", "application/fhir+json")
                    .header(
                        "Authorization",
                        "Bearer "
                            + tokenService
                                .issueSystemToken(
                                    TestAuth.CLIENT_ID, java.util.List.of("system/Claim.r"))
                                .accessToken())
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            context
                                .newJsonParser()
                                .encodeResourceToString(rawBundle ? inquiry : input)))
                    .build(),
                HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertMatchesGolden(
        context.newJsonParser().parseResource(Parameters.class, response.body()),
        "pas/inquire-response.json");
    assertThat(payerHapi.transactions()).hasSize(1);
  }

  @Test
  void storesTheClaimForTheMemberWithItsDecisionDeadline() {
    givenActiveMember("member-sandra");

    submit("request-mri.json");

    Bundle transaction = payerHapi.transactions().get(0);
    Claim claim = resource(transaction, Claim.class);
    assertThat(claim.getPatient().getReference()).isEqualTo("Patient/member-sandra");
    assertThat(claim.getInsuranceFirstRep().getCoverage().getReference())
        .isEqualTo("Coverage/payer-coverage");
    assertThat(claim.getInsurer().getReference()).isEqualTo("Organization/praxis-health-plan");
    assertThat(claim.getProvider().getReference())
        .isEqualTo("http://praxis.clinic/fhir/Organization/praxis-clinic");
    var orderer = claim.getCareTeamFirstRep().getProvider();
    assertThat(orderer.hasReference()).isFalse();
    assertThat(orderer.getType()).isEqualTo("PractitionerRole");
    assertThat(orderer.getIdentifier().getSystem())
        .isEqualTo("http://praxis.clinic/fhir/identifier/practitioner-role");
    assertThat(orderer.getIdentifier().getValue())
        .isEqualTo("2aee35b2-a615-337b-a6d2-8dd9ba6b5c21");
    assertThat(
            ((DateTimeType)
                    claim.getExtensionByUrl(ClaimSubmitService.DECISION_DEADLINE).getValue())
                .getValueAsString())
        .as("standard request created 2026-08-20T15:15:00Z, 7 days")
        .isEqualTo("2026-08-27T15:15:00+00:00");
    assertThat(transaction.getEntry().get(transaction.getEntry().size() - 2).getRequest())
        .extracting(Bundle.BundleEntryRequestComponent::getIfNoneExist)
        .isEqualTo(
            "identifier=http%3A%2F%2Fpraxis.clinic%2Ffhir%2Fidentifier%2Fclaim%7CPX-PA-mri-lumbar-spine-sandra-okafor");

    QuestionnaireResponse answers = resource(transaction, QuestionnaireResponse.class);
    assertThat(answers.getSubject().getReference()).isEqualTo("Patient/member-sandra");
    String answersUrl =
        transaction.getEntry().stream()
            .filter(entry -> entry.getResource() instanceof QuestionnaireResponse)
            .findFirst()
            .orElseThrow()
            .getFullUrl();
    assertThat(claim.getSupportingInfoFirstRep().getValueReference().getReference())
        .isEqualTo(answersUrl);
    ClaimResponse decision = resource(transaction, ClaimResponse.class);
    assertThat(decision.getPatient().getReference()).isEqualTo("Patient/member-sandra");
    assertThat(decision.getRequest().getReference())
        .isEqualTo(transaction.getEntry().get(transaction.getEntry().size() - 2).getFullUrl());
  }

  @Test
  void emergencyServiceTypeUsesTheRequestedOrderPriority() {
    givenActiveMember("member-gerald");
    Bundle request = fixture(Bundle.class, "pas/request-polysomnography.json");
    CodeableConcept emergency =
        new CodeableConcept(new Coding("https://codesystem.x12.org/005010/1365", "86", null));
    resource(request, Claim.class).getItemFirstRep().setProductOrService(emergency.copy());
    ServiceRequest order = resource(request, ServiceRequest.class);
    order.setCode(emergency);
    order.setPriority(ServiceRequest.ServiceRequestPriority.STAT);
    assertThat(resource(submit(request), ClaimResponse.class).getDisposition())
        .isEqualTo("No prior authorization needed");
    order.setPriority(ServiceRequest.ServiceRequestPriority.URGENT);
    assertThat(resource(submit(request), ClaimResponse.class).getDisposition())
        .isEqualTo("Pended for clinical review");
  }

  @Test
  void expeditedRequestIsDueIn72Hours() {
    givenActiveMember("member-gerald");
    Bundle request = fixture(Bundle.class, "pas/request-polysomnography.json");
    ((Claim) request.getEntryFirstRep().getResource())
        .setPriority(
            new CodeableConcept(
                new Coding("http://terminology.hl7.org/CodeSystem/processpriority", "stat", null)));

    submit(request);

    Claim claim = resource(payerHapi.transactions().get(0), Claim.class);
    assertThat(
            ((DateTimeType)
                    claim.getExtensionByUrl(ClaimSubmitService.DECISION_DEADLINE).getValue())
                .getValueAsString())
        .isEqualTo("2026-08-23T15:15:00+00:00");
  }

  @Test
  void resubmittingTheSameIdentifierReturnsTheFirstDecision() {
    givenActiveMember("member-sandra");
    Bundle first = submit("request-mri.json");
    givenExistingDecision(false);

    Bundle second = submit("request-mri.json");

    assertThat(payerHapi.transactions()).hasSize(1);
    assertThat(payerHapi.lastSearchCriteria(Claim.class))
        .contains(
            "identifier=http://praxis.clinic/fhir/identifier/claim|PX-PA-mri-lumbar-spine-sandra-okafor",
            "_revinclude=ClaimResponse:request");
    FhirContext context = FhirContext.forR4Cached();
    assertThat(context.newJsonParser().encodeResourceToString(second))
        .isEqualTo(context.newJsonParser().encodeResourceToString(first));
  }

  @ParameterizedTest
  @CsvSource({
    "false, request-cgm.json, member-ramon",
    "false, request-mri.json, member-ramon",
    "false, request-polysomnography.json, member-sandra",
    "true, request-mri.json, member-ramon",
    "true, request-polysomnography.json, member-sandra"
  })
  void identifierCollisionDoesNotReturnTheStoredDecision(
      boolean conditionalMatch, String laterFixture, String laterBeneficiary) {
    givenActiveMember("member-sandra");
    submit("request-mri.json");
    givenExistingDecision(conditionalMatch);
    givenActiveMember(laterBeneficiary);
    Bundle later = fixture(Bundle.class, "pas/" + laterFixture);
    resource(later, Claim.class)
        .getIdentifierFirstRep()
        .setValue("PX-PA-mri-lumbar-spine-sandra-okafor");

    assertThatThrownBy(() -> submit(later))
        .isInstanceOfSatisfying(
            ResourceVersionConflictException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(409);
              String outcome =
                  FhirContext.forR4Cached()
                      .newJsonParser()
                      .encodeResourceToString(error.getOperationOutcome());
              assertThat(outcome)
                  .contains("Claim identifier is already in use")
                  .doesNotContain("PHP-PA-", "Not Certified", "member-sandra");
            });
    assertThat(payerHapi.transactions()).hasSize(conditionalMatch ? 2 : 1);
  }

  @Test
  void conditionalCreateMatchReturnsTheExistingDecisionForAValidReplay() {
    givenActiveMember("member-sandra");
    Bundle initial = submit("request-mri.json");
    givenExistingDecision(true);

    Bundle replay = submit("request-mri.json");

    FhirContext context = FhirContext.forR4Cached();
    assertThat(context.newJsonParser().encodeResourceToString(replay))
        .isEqualTo(context.newJsonParser().encodeResourceToString(initial));
    Bundle transaction = payerHapi.transactions().get(1);
    assertThat(transaction.getEntry().getLast().getRequest().getIfNoneExist())
        .isEqualTo(
            "request="
                + transaction.getEntry().get(transaction.getEntry().size() - 2).getFullUrl());
  }

  @Test
  void conditionalCreateMatchAcceptsABareStatusCode() {
    givenActiveMember("member-sandra");
    Bundle initial = submit("request-mri.json");
    givenExistingDecision(true, "200", "200", "Claim/2");

    Bundle replay = submit("request-mri.json");

    FhirContext context = FhirContext.forR4Cached();
    assertThat(context.newJsonParser().encodeResourceToString(replay))
        .isEqualTo(context.newJsonParser().encodeResourceToString(initial));
  }

  @Test
  void conditionalCreateMatchWithoutAnExistingDecisionIsAServerError() {
    givenActiveMember("member-sandra");
    submit("request-mri.json");
    givenExistingDecision(true, "200 OK", "201 Created", "Claim/2");

    assertThatThrownBy(() -> submit("request-mri.json"))
        .isInstanceOfSatisfying(
            InternalErrorException.class,
            error ->
                assertThat(error.getMessage()).contains("Prior authorization has no existing"));
  }

  @Test
  void conditionalCreateMatchWithADecisionForAnotherClaimIsAServerError() {
    givenActiveMember("member-sandra");
    submit("request-mri.json");
    givenExistingDecision(true, "200 OK", "200 OK", "Claim/99");

    assertThatThrownBy(() -> submit("request-mri.json"))
        .isInstanceOfSatisfying(
            InternalErrorException.class,
            error -> assertThat(error.getMessage()).contains("decision does not match"));
  }

  @Test
  void replayAfterTheCoveragePeriodEndsReturnsTheStoredDecision() {
    givenActiveMember("member-sandra");
    Bundle first = submit("request-mri.json");
    givenExistingDecision(false);
    givenLapsedMember("member-sandra");

    Bundle replay = submit("request-mri.json");

    FhirContext context = FhirContext.forR4Cached();
    assertThat(context.newJsonParser().encodeResourceToString(replay))
        .isEqualTo(context.newJsonParser().encodeResourceToString(first));
    assertThat(payerHapi.transactions()).hasSize(1);
  }

  @Test
  void anotherPatientOnTheSameIdentifierAfterTheCoveragePeriodEndsStillConflicts() {
    givenActiveMember("member-sandra");
    submit("request-mri.json");
    givenExistingDecision(false);
    givenLapsedMember("member-ramon");
    Bundle later = fixture(Bundle.class, "pas/request-mri.json");

    assertThatThrownBy(() -> submit(later))
        .isInstanceOfSatisfying(
            ResourceVersionConflictException.class,
            error -> {
              assertThat(error.getStatusCode()).isEqualTo(409);
              assertThat(
                      FhirContext.forR4Cached()
                          .newJsonParser()
                          .encodeResourceToString(error.getOperationOutcome()))
                  .doesNotContain("PHP-PA-", "Not Certified", "member-sandra");
            });
  }

  @Test
  void answersAreStoredWithAStableIdentifierSoARacingSubmitCannotDuplicateThem() {
    givenActiveMember("member-sandra");

    submit("request-mri.json");

    Bundle.BundleEntryComponent entry =
        payerHapi.transactions().getFirst().getEntry().stream()
            .filter(candidate -> candidate.getResource() instanceof QuestionnaireResponse)
            .findFirst()
            .orElseThrow();
    Identifier identifier = ((QuestionnaireResponse) entry.getResource()).getIdentifier();
    assertThat(identifier.getSystem()).isEqualTo(ClaimSubmitService.QUESTIONNAIRE_RESPONSE_SYSTEM);
    assertThat(entry.getRequest().getIfNoneExist())
        .contains("identifier=")
        .contains(
            java.net.URLEncoder.encode(
                identifier.getSystem() + "|" + identifier.getValue(),
                java.nio.charset.StandardCharsets.UTF_8));
  }

  private static void givenExistingDecision(boolean conditionalMatch) {
    givenExistingDecision(conditionalMatch, "200 OK", "200 OK", "Claim/2");
  }

  private static void givenExistingDecision(
      boolean conditionalMatch, String claimStatus, String responseStatus, String decisionRequest) {
    Bundle stored = payerHapi.transactions().getFirst();
    Claim existing = resource(stored, Claim.class);
    existing.setId("Claim/2");
    ClaimResponse decision = resource(stored, ClaimResponse.class);
    decision.setId("ClaimResponse/3");
    decision.setRequest(new Reference(decisionRequest));
    if (conditionalMatch) {
      payerHapi.givenStored(existing, "2");
      payerHapi.givenStored(decision, "3");
      payerHapi.givenTransactionResult(
          () -> {
            Bundle transaction = payerHapi.transactions().getLast();
            Bundle result = new Bundle().setType(Bundle.BundleType.TRANSACTIONRESPONSE);
            transaction.getEntry().stream()
                .filter(entry -> entry.getResource() instanceof QuestionnaireResponse)
                .forEach(entry -> result.addEntry().getResponse().setStatus("201 Created"));
            result
                .addEntry()
                .getResponse()
                .setStatus(claimStatus)
                .setLocation("Claim/2/_history/1");
            result
                .addEntry()
                .getResponse()
                .setStatus(responseStatus)
                .setLocation("ClaimResponse/3/_history/1");
            return result;
          });
    } else {
      payerHapi.givenSearchResult(Claim.class, FhirClientStub.bundleOf(existing, decision));
    }
  }

  @Test
  void bundleThatFailsValidationGetsTheValidatorsOperationOutcome() {
    payerHapi.givenOperationResult(
        () -> {
          OperationOutcome outcome = new OperationOutcome();
          outcome
              .addIssue()
              .setSeverity(OperationOutcome.IssueSeverity.ERROR)
              .setCode(OperationOutcome.IssueType.REQUIRED)
              .setDiagnostics("Bundle.timestamp: minimum required = 1, but only found 0");
          outcome
              .addIssue()
              .setSeverity(OperationOutcome.IssueSeverity.WARNING)
              .setDiagnostics("dom-6");
          throw new UnprocessableEntityException(FhirContext.forR4Cached(), outcome);
        });

    assertThatThrownBy(() -> submit("request-mri.json"))
        .isInstanceOfSatisfying(
            UnprocessableEntityException.class,
            e ->
                assertThat(((OperationOutcome) e.getOperationOutcome()).getIssue())
                    .singleElement()
                    .extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
                    .asString()
                    .contains("Bundle.timestamp"));
    assertThat(payerHapi.transactions()).isEmpty();
  }

  @Test
  void validatesAgainstThePasRequestBundleProfile() {
    givenActiveMember("member-sandra");

    Bundle request = fixture(Bundle.class, "pas/request-mri.json");
    request.getMeta().getProfile().clear();
    submit(request);

    Parameters input = (Parameters) payerHapi.operationInputs().get(0);
    assertThat(input.getParameter()).hasSize(1);
    Bundle validated = (Bundle) input.getParameter("resource").getResource();
    assertThat(validated.getMeta().hasProfile(ClaimSubmitService.PAS_REQUEST_BUNDLE)).isTrue();
  }

  @Test
  void bundleWithoutAClaimIsABadRequest() {
    Bundle request = fixture(Bundle.class, "pas/request-mri.json");
    request.getEntry().remove(0);

    assertThatThrownBy(() -> submit(request))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("exactly one Claim");
  }

  @Test
  void rawBundleBodyIsAcceptedLikeTheResourceParameter() throws Exception {
    givenActiveMember("member-sandra");
    String body =
        FhirContext.forR4Cached()
            .newJsonParser()
            .encodeResourceToString(fixture(Bundle.class, "pas/request-mri.json"));

    HttpResponse<String> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/fhir/Claim/$submit"))
                    .header("Content-Type", "application/fhir+json")
                    .header(
                        "Authorization",
                        "Bearer "
                            + tokenService
                                .issueSystemToken(TestAuth.CLIENT_ID, TestAuth.CLIENT_SCOPES)
                                .accessToken())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("profile-pas-response-bundle", "Not Certified");
  }

  @Test
  void memberTokenIsForbidden() {
    IGenericClient member =
        withToken(tokenService.issueMemberToken(TestAuth.CLIENT_ID, "member-sandra").accessToken());

    assertThatThrownBy(() -> submit(member, fixture(Bundle.class, "pas/request-mri.json")))
        .isInstanceOf(ForbiddenOperationException.class);
  }

  @Test
  void submitWritesAnAuditEvent() {
    givenActiveMember("member-sandra");

    submit("request-mri.json");

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(payerHapi.created(AuditEvent.class))
                    .anySatisfy(
                        event -> {
                          assertThat(event.getEntityFirstRep().getName())
                              .isEqualTo("Claim/$submit");
                          assertThat(event.getEntityFirstRep().getWhat().getIdentifier().getValue())
                              .isEqualTo("PX-PA-mri-lumbar-spine-sandra-okafor");
                          assertThat(event.getAgentFirstRep().getWho().getIdentifier().getValue())
                              .isEqualTo(TestAuth.CLIENT_ID);
                          assertThat(event.getOutcome()).isEqualTo(AuditEvent.AuditEventOutcome._0);
                          assertThat(event.getEntity())
                              .extracting(entity -> entity.getWhat().getReference())
                              .contains("Claim/2", "ClaimResponse/3");
                        }));
  }

  private static void givenLapsedMember(String memberPatient) {
    givenMember(memberPatient, new DateTimeType("2026-08-01"));
  }

  private static void givenActiveMember(String memberPatient) {
    givenMember(memberPatient, null);
  }

  private static void givenMember(String memberPatient, DateTimeType periodEnd) {
    Coverage coverage = new Coverage();
    coverage.setId("payer-coverage");
    coverage.setStatus(Coverage.CoverageStatus.ACTIVE);
    coverage.setPeriod(
        new Period().setStartElement(new DateTimeType("2025-08-20")).setEndElement(periodEnd));
    coverage.addIdentifier(
        new Identifier()
            .setSystem("http://praxishealthplan.example/fhir/identifier/member-id")
            .setValue("member"));
    coverage.setBeneficiary(new Reference("Patient/" + memberPatient));
    coverage.addPayor(new Reference("Organization/praxis-health-plan"));
    payerHapi.givenSearchResult(Coverage.class, FhirClientStub.bundleOf(coverage));
    payerHapi.givenStored(coverage.copy(), "payer-coverage");
  }

  private static <T extends org.hl7.fhir.r4.model.Resource> T resource(
      Bundle bundle, Class<T> type) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(type::isInstance)
        .map(type::cast)
        .findFirst()
        .orElseThrow();
  }

  private Bundle submit(String fixture) {
    return submit(fixture(Bundle.class, "pas/" + fixture));
  }

  private Bundle submit(Bundle request) {
    return submit(clinicApi, request);
  }

  private static Bundle submit(IGenericClient client, Bundle request) {
    Parameters input = new Parameters();
    input.addParameter().setName("resource").setResource(request);
    return client
        .operation()
        .onType(Claim.class)
        .named(ClaimSubmitProvider.OPERATION)
        .withParameters(input)
        .returnResourceType(Bundle.class)
        .execute();
  }

  private IGenericClient withToken(String token) {
    FhirContext fhirContext = FhirContext.forR4Cached();
    fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
    IGenericClient client =
        fhirContext.newRestfulGenericClient("http://localhost:" + port + "/fhir");
    client.registerInterceptor(new BearerTokenAuthInterceptor(token));
    return client;
  }
}
