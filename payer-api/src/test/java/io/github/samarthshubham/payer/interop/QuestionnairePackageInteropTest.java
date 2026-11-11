package io.github.samarthshubham.payer.interop;

import static io.github.samarthshubham.payer.support.GoldenFhir.assertMatchesGolden;
import static io.github.samarthshubham.payer.support.GoldenFhir.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import io.github.samarthshubham.payer.service.TokenService;
import io.github.samarthshubham.payer.support.FhirClientStub;
import io.github.samarthshubham.payer.support.TestAuth;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.convention.TestBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuestionnairePackageInteropTest {

  private static final String CGM =
      "http://praxishealthplan.example/fhir/Questionnaire/continuous-glucose-monitor";
  private static final FhirClientStub PAYER_HAPI = new FhirClientStub();

  @TestBean private IGenericClient fhirClient;

  @TestBean private Clock clock;

  @LocalServerPort private int port;

  @Autowired private TokenService tokenService;

  private IGenericClient clinicApi;

  static IGenericClient fhirClient() {
    return PAYER_HAPI.client();
  }

  static Clock clock() {
    return Clock.fixed(Instant.parse("2026-09-14T15:30:00Z"), ZoneOffset.UTC);
  }

  @BeforeEach
  void storeTheCgmPackageInThePayerHapi() {
    PAYER_HAPI.givenSearchResult(
        Questionnaire.class,
        FhirClientStub.bundleOf(
            fixture(Questionnaire.class, "dtr/questionnaire-continuous-glucose-monitor.json")));
    PAYER_HAPI.givenSearchResult(
        Library.class,
        FhirClientStub.bundleOf(
            fixture(Library.class, "dtr/library-continuous-glucose-monitor.json")));
    PAYER_HAPI.givenSearchResult(
        ValueSet.class,
        FhirClientStub.bundleOf(fixture(ValueSet.class, "dtr/valueset-diabetes-types.json")));

    clinicApi =
        withToken(
            tokenService
                .issueSystemToken(TestAuth.CLIENT_ID, TestAuth.CLIENT_SCOPES)
                .accessToken());
  }

  @Test
  void goldenInputGetsTheGoldenPackage() {
    Parameters output =
        questionnairePackage(fixture(Parameters.class, "dtr/questionnaire-package-input.json"));

    assertMatchesGolden(output, "dtr/questionnaire-package-output-cgm.json");
    assertThat(PAYER_HAPI.lastSearchCriteria(Questionnaire.class)).contains("url=" + CGM);
  }

  @Test
  void questionnaireCanonicalWithoutAnOrderGetsItsPackage() {
    Parameters input = new Parameters();
    input.addParameter().setName("coverage").setResource(coverage());
    input.addParameter().setName("questionnaire").setValue(new CanonicalType(CGM));

    Parameters output = questionnairePackage(input);

    assertThat(output.getParameter())
        .extracting(Parameters.ParametersParameterComponent::getName)
        .containsExactly("packagebundle");
  }

  @Test
  void requestWithoutCoverageIsABadRequest() {
    Parameters input = new Parameters();
    input.addParameter().setName("questionnaire").setValue(new CanonicalType(CGM));

    assertThatThrownBy(() -> questionnairePackage(input))
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            e ->
                assertThat(((OperationOutcome) e.getOperationOutcome()).getIssueFirstRep())
                    .extracting(OperationOutcome.OperationOutcomeIssueComponent::getDiagnostics)
                    .asString()
                    .contains("coverage"));
  }

  @Test
  void requestWithOnlyACoverageIsABadRequest() {
    Parameters input = new Parameters();
    input.addParameter().setName("coverage").setResource(coverage());

    assertThatThrownBy(() -> questionnairePackage(input))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("order, questionnaire or context");
  }

  @Test
  void coverageWithoutABeneficiaryIsABadRequest() {
    Coverage coverage = coverage();
    coverage.setBeneficiary(null);
    Parameters input = new Parameters();
    input.addParameter().setName("coverage").setResource(coverage);
    input.addParameter().setName("questionnaire").setValue(new CanonicalType(CGM));

    assertThatThrownBy(() -> questionnairePackage(input))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("beneficiary");
  }

  @Test
  void memberTokenIsForbiddenEvenForAnotherMembersCoverage() {
    assertThatThrownBy(
            () ->
                questionnairePackage(
                    withToken(
                        tokenService
                            .issueMemberToken(TestAuth.CLIENT_ID, "someone-else")
                            .accessToken()),
                    fixture(Parameters.class, "dtr/questionnaire-package-input.json")))
        .isInstanceOf(ForbiddenOperationException.class);
  }

  private IGenericClient withToken(String token) {
    FhirContext fhirContext = FhirContext.forR4Cached();
    fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
    IGenericClient client =
        fhirContext.newRestfulGenericClient("http://localhost:" + port + "/fhir");
    client.registerInterceptor(new BearerTokenAuthInterceptor(token));
    return client;
  }

  private Parameters questionnairePackage(Parameters input) {
    return questionnairePackage(clinicApi, input);
  }

  private static Parameters questionnairePackage(IGenericClient client, Parameters input) {
    return client
        .operation()
        .onType(Questionnaire.class)
        .named(QuestionnairePackageProvider.OPERATION)
        .withParameters(input)
        .execute();
  }

  private static Coverage coverage() {
    return (Coverage)
        fixture(Parameters.class, "dtr/questionnaire-package-input.json")
            .getParameter("coverage")
            .getResource();
  }
}
