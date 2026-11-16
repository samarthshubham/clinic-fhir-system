package io.github.samarthshubham.payer.interop;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.UnclassifiedServerFailureException;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilderAppliesTo;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilderOperationNamedAndScoped;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRuleBuilderRuleOpClassifier;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import io.github.samarthshubham.payer.config.JwtConfig;
import io.github.samarthshubham.payer.security.SmartPermission;
import io.github.samarthshubham.payer.security.SmartScope;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.IdType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class ScopeInterceptor extends AuthorizationInterceptor {

  private static final Set<RestOperationTypeEnum> SEARCHES =
      Set.of(RestOperationTypeEnum.SEARCH_TYPE, RestOperationTypeEnum.SEARCH_SYSTEM);
  private static final Set<RestOperationTypeEnum> UPDATES =
      Set.of(RestOperationTypeEnum.UPDATE, RestOperationTypeEnum.PATCH);

  private final FhirContext fhirContext;
  private final List<OperationScope> operationScopes;

  public ScopeInterceptor(FhirContext fhirContext, List<OperationScope> operationScopes) {
    this.fhirContext = fhirContext;
    this.operationScopes = List.copyOf(operationScopes);
  }

  @Override
  public List<IAuthRule> buildRuleList(RequestDetails request) {
    RuleBuilder rules = new RuleBuilder();
    rules.allow().metadata();
    Jwt token = currentToken();
    if (token == null) {
      if (request.getRestOperationType() != RestOperationTypeEnum.METADATA) {
        throw authenticationRequired();
      }
      return rules.build();
    }
    // The per-request permission gating below cannot see inside a Bundle, so batch and
    // transaction requests get no rules and are denied until a story needs them.
    if (request.getRestOperationType() == RestOperationTypeEnum.TRANSACTION) {
      return rules.build();
    }
    String patient = token.getClaimAsString(JwtConfig.PATIENT_CLAIM);
    IdType member = patient == null ? null : new IdType("Patient", patient);
    for (SmartScope scope : SmartScope.parseAll(token.getClaimAsString(JwtConfig.SCOPE_CLAIM))) {
      boolean applies =
          member == null
              ? scope.context() == SmartScope.Context.SYSTEM
              : scope.context() == SmartScope.Context.PATIENT;
      if (applies) {
        allowInteractions(rules, scope, request.getRestOperationType(), member);
        allowOperations(rules, scope, member);
      }
    }
    return rules.build();
  }

  private void allowInteractions(
      RuleBuilder rules, SmartScope scope, RestOperationTypeEnum operation, IdType member) {
    for (SmartPermission permission : scope.permissions()) {
      IAuthRuleBuilderAppliesTo<IAuthRuleBuilderRuleOpClassifier> rule =
          switch (permission) {
            case CREATE -> rules.allow().create();
            case READ -> SEARCHES.contains(operation) ? null : rules.allow().read();
            case SEARCH -> SEARCHES.contains(operation) ? rules.allow().read() : null;
            case UPDATE -> UPDATES.contains(operation) ? rules.allow().write() : null;
            case DELETE -> rules.allow().delete();
          };
      if (rule != null) {
        IAuthRuleBuilderRuleOpClassifier classifier =
            scope.anyResource() ? rule.allResources() : rule.resourcesOfType(scope.resourceType());
        if (member == null) {
          classifier.withAnyId();
        } else {
          classifier.inCompartment("Patient", member);
        }
      }
    }
  }

  private void allowOperations(RuleBuilder rules, SmartScope scope, IdType member) {
    for (OperationScope operation : operationScopes) {
      if ((member == null || !operation.systemOnly())
          && scope.grants(operation.resourceType(), operation.permission())) {
        Class<? extends IBaseResource> type =
            fhirContext.getResourceDefinition(operation.resourceType()).getImplementingClass();
        respond(rules.allow().operation().named(operation.operation()).onType(type), member);
        respond(
            rules.allow().operation().named(operation.operation()).onInstancesOfType(type), member);
      }
    }
  }

  private static void respond(IAuthRuleBuilderOperationNamedAndScoped rule, IdType member) {
    if (member == null) {
      rule.andAllowAllResponses();
    } else {
      rule.andRequireExplicitResponseAuthorization();
    }
  }

  private static Jwt currentToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getPrincipal() instanceof Jwt jwt ? jwt : null;
  }

  // HAPI writes AuthenticationException as text/plain; this keeps the 401 an OperationOutcome.
  private static UnclassifiedServerFailureException authenticationRequired() {
    UnclassifiedServerFailureException exception =
        new UnclassifiedServerFailureException(401, "Authentication required");
    exception.addResponseHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    return exception;
  }
}
