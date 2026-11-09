package io.github.samarthshubham.payer.interop;

import static io.github.samarthshubham.payer.support.TestInteropProviders.CLAIM_A;
import static io.github.samarthshubham.payer.support.TestInteropProviders.CLAIM_B;
import static io.github.samarthshubham.payer.support.TestInteropProviders.MAPPED_OPERATION;
import static io.github.samarthshubham.payer.support.TestInteropProviders.MEMBER_A;
import static io.github.samarthshubham.payer.support.TestInteropProviders.MEMBER_B;
import static io.github.samarthshubham.payer.support.TestInteropProviders.QUESTIONNAIRE;
import static io.github.samarthshubham.payer.support.TestInteropProviders.UNMAPPED_OPERATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.server.exceptions.AuthenticationException;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import io.github.samarthshubham.payer.security.Role;
import io.github.samarthshubham.payer.service.TokenService;
import io.github.samarthshubham.payer.support.TestAuth;
import io.github.samarthshubham.payer.support.TestInteropProviders;
import java.util.List;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestSecurityComponent;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.UriType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestInteropProviders.class)
class ScopeInterceptorInteropTest {

  @LocalServerPort private int port;

  @Autowired private TokenService tokenService;

  @MockitoBean private IGenericClient payerHapiClient;

  private FhirContext fhirContext;

  @BeforeEach
  void setUpClientContext() {
    fhirContext = FhirContext.forR4();
    fhirContext.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);
  }

  @Test
  void systemTokenWithTheRightScopeCreatesAClaim() {
    Claim claim = new Claim();
    claim.setPatient(new Reference("Patient/" + MEMBER_A));

    assertThat(clinicApi().create().resource(claim).execute().getCreated()).isTrue();
  }

  @Test
  void systemTokenWithTheRightScopeReadsAQuestionnaire() {
    assertThat(clinicApi().read().resource(Questionnaire.class).withId(QUESTIONNAIRE).execute())
        .isNotNull();
  }

  @Test
  void wrongScopeIsForbiddenWithAnOperationOutcome() {
    assertForbidden(() -> clinicApi().read().resource(Claim.class).withId(CLAIM_A).execute());
  }

  @Test
  void readScopeDoesNotAllowSearch() {
    IGenericClient client = withToken(systemToken("system/Patient.r"));

    assertThat(client.read().resource(Patient.class).withId(MEMBER_A).execute()).isNotNull();
    assertForbidden(() -> client.search().forResource(Patient.class).execute());
  }

  @Test
  void searchScopeAllowsSearch() {
    IGenericClient client = withToken(systemToken("system/Patient.s"));

    assertThat(client.search().forResource(Patient.class).execute()).isNotNull();
  }

  @Test
  void mappedOperationNeedsItsScope() {
    assertThat(callOperation(clinicApi(), MAPPED_OPERATION)).isNotNull();
    assertForbidden(
        () -> callOperation(withToken(systemToken("system/Questionnaire.r")), MAPPED_OPERATION));
  }

  @Test
  void operationWithoutADeclaredScopeIsDenied() {
    assertForbidden(
        () -> callOperation(withToken(systemToken("system/*.cruds")), UNMAPPED_OPERATION));
  }

  @Test
  void memberReadsTheirOwnPatientAndClaim() {
    IGenericClient memberA = withToken(memberToken(MEMBER_A));

    assertThat(memberA.read().resource(Patient.class).withId(MEMBER_A).execute()).isNotNull();
    assertThat(memberA.read().resource(Claim.class).withId(CLAIM_A).execute()).isNotNull();
  }

  @Test
  void memberAReadingMemberBIsForbidden() {
    IGenericClient memberA = withToken(memberToken(MEMBER_A));

    assertForbidden(() -> memberA.read().resource(Patient.class).withId(MEMBER_B).execute());
    assertForbidden(() -> memberA.read().resource(Claim.class).withId(CLAIM_B).execute());
  }

  @Test
  void memberCannotWrite() {
    Claim claim = new Claim();
    claim.setPatient(new Reference("Patient/" + MEMBER_A));

    assertForbidden(() -> withToken(memberToken(MEMBER_A)).create().resource(claim).execute());
  }

  @Test
  void patientScopesWithoutAPatientClaimGrantNothing() {
    assertForbidden(
        () ->
            withToken(systemToken("patient/*.r"))
                .read()
                .resource(Patient.class)
                .withId(MEMBER_A)
                .execute());
  }

  @Test
  void staffTokenHasNoInteropAccess() {
    String staff = tokenService.issueStaffTokens(TestAuth.user(Role.UM_REVIEWER)).accessToken();

    assertForbidden(
        () -> withToken(staff).read().resource(Patient.class).withId(MEMBER_A).execute());
  }

  @Test
  void missingTokenIsUnauthorizedWithAnOperationOutcome() {
    assertThatThrownBy(() -> anonymous().read().resource(Patient.class).withId(MEMBER_A).execute())
        .isInstanceOf(AuthenticationException.class)
        .satisfies(error -> assertOperationOutcome((BaseServerResponseException) error));
  }

  @Test
  void invalidTokenIsUnauthorizedWithAnOperationOutcome() {
    assertThatThrownBy(
            () -> withToken("not-a-jwt").read().resource(Patient.class).withId(MEMBER_A).execute())
        .isInstanceOf(AuthenticationException.class)
        .satisfies(error -> assertOperationOutcome((BaseServerResponseException) error));
  }

  @Test
  void metadataStaysPublicAndAdvertisesSmartSecurity() {
    CapabilityStatement metadata =
        anonymous().capabilities().ofType(CapabilityStatement.class).execute();

    CapabilityStatementRestSecurityComponent security = metadata.getRestFirstRep().getSecurity();
    assertThat(security.getServiceFirstRep().getCodingFirstRep().getCode())
        .isEqualTo("SMART-on-FHIR");
    assertThat(
            ((UriType)
                    security
                        .getExtensionByUrl(SmartCapabilityInterceptor.OAUTH_URIS)
                        .getExtensionByUrl("token")
                        .getValue())
                .getValue())
        .isEqualTo("http://localhost:" + port + "/oauth/token");
  }

  private IGenericClient clinicApi() {
    return withToken(systemToken(TestAuth.CLIENT_SCOPES.toArray(String[]::new)));
  }

  private String systemToken(String... scopes) {
    return tokenService.issueSystemToken(TestAuth.CLIENT_ID, List.of(scopes)).accessToken();
  }

  private String memberToken(String patientId) {
    return tokenService.issueMemberToken(TestAuth.CLIENT_ID, patientId).accessToken();
  }

  private IGenericClient anonymous() {
    return fhirContext.newRestfulGenericClient("http://localhost:" + port + "/fhir");
  }

  private IGenericClient withToken(String token) {
    IGenericClient client = anonymous();
    client.registerInterceptor(new BearerTokenAuthInterceptor(token));
    return client;
  }

  private static Parameters callOperation(IGenericClient client, String operation) {
    return client
        .operation()
        .onType(Claim.class)
        .named(operation)
        .withParameters(new Parameters())
        .execute();
  }

  private static void assertForbidden(Runnable call) {
    assertThatThrownBy(call::run)
        .isInstanceOf(ForbiddenOperationException.class)
        .satisfies(error -> assertOperationOutcome((BaseServerResponseException) error));
  }

  private static void assertOperationOutcome(BaseServerResponseException error) {
    assertThat(error.getOperationOutcome()).isInstanceOf(OperationOutcome.class);
  }
}
