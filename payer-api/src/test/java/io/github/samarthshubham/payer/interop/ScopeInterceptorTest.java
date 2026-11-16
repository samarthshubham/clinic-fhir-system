package io.github.samarthshubham.payer.interop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import io.github.samarthshubham.payer.config.JwtConfig;
import io.github.samarthshubham.payer.security.SmartPermission;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ScopeInterceptorTest {

  private final ScopeInterceptor interceptor =
      new ScopeInterceptor(FhirContext.forR4Cached(), List.of());

  @BeforeEach
  void authenticateWithEveryScope() {
    Jwt jwt =
        new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("alg", "HS256"),
            Map.of(JwtConfig.SCOPE_CLAIM, "system/*.cruds"));
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void batchAndTransactionBundlesGetOnlyTheMetadataRule() {
    assertThat(interceptor.buildRuleList(request(RestOperationTypeEnum.TRANSACTION))).hasSize(1);
  }

  @Test
  void otherInteractionsGetTheScopeRules() {
    assertThat(interceptor.buildRuleList(request(RestOperationTypeEnum.READ)))
        .hasSizeGreaterThan(1);
  }

  @Test
  void memberTokensGetNoRuleForASystemOnlyOperation() {
    ScopeInterceptor withOperations =
        new ScopeInterceptor(
            FhirContext.forR4Cached(),
            List.of(
                new OperationScope("$for-members", "Questionnaire", SmartPermission.READ),
                new OperationScope("$system-only", "Questionnaire", SmartPermission.READ, true)));
    RequestDetails operation = request(RestOperationTypeEnum.EXTENDED_OPERATION_TYPE);

    authenticate(Map.of(JwtConfig.SCOPE_CLAIM, "patient/*.r", JwtConfig.PATIENT_CLAIM, "a"));
    int memberRules = withOperations.buildRuleList(operation).size();
    authenticate(Map.of(JwtConfig.SCOPE_CLAIM, "system/*.r"));
    int systemRules = withOperations.buildRuleList(operation).size();

    assertThat(systemRules).isEqualTo(memberRules + 2);
  }

  private static void authenticate(Map<String, Object> claims) {
    Jwt jwt =
        new Jwt(
            "token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "HS256"), claims);
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
  }

  private static RequestDetails request(RestOperationTypeEnum operation) {
    RequestDetails request = mock(RequestDetails.class);
    when(request.getRestOperationType()).thenReturn(operation);
    return request;
  }
}
