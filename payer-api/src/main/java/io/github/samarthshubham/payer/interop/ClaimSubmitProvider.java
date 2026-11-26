package io.github.samarthshubham.payer.interop;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import io.github.samarthshubham.payer.config.JwtConfig;
import io.github.samarthshubham.payer.service.ClaimSubmitService;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ClaimSubmitProvider implements IResourceProvider {

  public static final String OPERATION = "$submit";

  private final ClaimSubmitService claimSubmitService;

  public ClaimSubmitProvider(ClaimSubmitService claimSubmitService) {
    this.claimSubmitService = claimSubmitService;
  }

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return Claim.class;
  }

  @Operation(
      name = OPERATION,
      idempotent = false,
      canonicalUrl = "http://hl7.org/fhir/us/davinci-pas/OperationDefinition/Claim-submit")
  public Bundle submit(@OperationParam(name = "resource", min = 1, max = 1) Bundle bundle) {
    if (bundle == null) {
      throw new InvalidRequestException("Send the PAS request Bundle as the resource parameter");
    }
    return claimSubmitService.submit(bundle, clientId());
  }

  private static String clientId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getPrincipal() instanceof Jwt jwt
        ? jwt.getClaimAsString(JwtConfig.CLIENT_ID_CLAIM)
        : null;
  }
}
