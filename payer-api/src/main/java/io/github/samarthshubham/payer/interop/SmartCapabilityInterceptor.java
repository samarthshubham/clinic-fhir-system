package io.github.samarthshubham.payer.interop;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import org.hl7.fhir.instance.model.api.IBaseConformance;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestSecurityComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.UriType;

@Interceptor
public class SmartCapabilityInterceptor {

  static final String SECURITY_SERVICE_SYSTEM =
      "http://terminology.hl7.org/CodeSystem/restful-security-service";
  static final String OAUTH_URIS =
      "http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris";

  @Hook(Pointcut.SERVER_CAPABILITY_STATEMENT_GENERATED)
  public void advertiseSmartSecurity(IBaseConformance capabilityStatement, RequestDetails request) {
    String tokenUrl = request.getFhirServerBase().replaceFirst("/fhir$", "/oauth/token");
    for (CapabilityStatementRestComponent rest :
        ((CapabilityStatement) capabilityStatement).getRest()) {
      CapabilityStatementRestSecurityComponent security =
          new CapabilityStatementRestSecurityComponent()
              .addService(
                  new CodeableConcept(
                      new Coding(SECURITY_SERVICE_SYSTEM, "SMART-on-FHIR", "SMART-on-FHIR")))
              .setDescription(
                  "SMART Backend Services client_credentials tokens with v2 system scopes;"
                      + " member tokens with patient scopes");
      Extension oauthUris = new Extension(OAUTH_URIS);
      oauthUris.addExtension("token", new UriType(tokenUrl));
      security.addExtension(oauthUris);
      rest.setSecurity(security);
    }
  }
}
