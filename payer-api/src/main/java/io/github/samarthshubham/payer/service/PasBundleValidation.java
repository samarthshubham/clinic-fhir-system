package io.github.samarthshubham.payer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;

final class PasBundleValidation {

  private PasBundleValidation() {}

  static void requireValid(
      IGenericClient fhirClient, FhirContext fhirContext, Bundle bundle, String profile) {
    Bundle profiled = bundle.copy();
    // The 8.12 validator resolves the profile from meta on a resource passed to $validate.
    if (!profiled.getMeta().hasProfile(profile)) {
      profiled.getMeta().addProfile(profile);
    }
    Parameters input = new Parameters();
    input.addParameter().setName("resource").setResource(profiled);
    OperationOutcome outcome;
    try {
      outcome =
          fhirClient
              .operation()
              .onType(Bundle.class)
              .named("$validate")
              .withParameters(input)
              .returnResourceType(OperationOutcome.class)
              .execute();
    } catch (PreconditionFailedException | UnprocessableEntityException e) {
      if (!(e.getOperationOutcome() instanceof OperationOutcome failed)) {
        throw e;
      }
      outcome = failed;
    }
    OperationOutcome errors = new OperationOutcome();
    outcome.getIssue().stream()
        .filter(
            issue ->
                issue.getSeverity() == OperationOutcome.IssueSeverity.ERROR
                    || issue.getSeverity() == OperationOutcome.IssueSeverity.FATAL)
        .forEach(issue -> errors.addIssue(issue.copy()));
    if (errors.hasIssue()) {
      throw new UnprocessableEntityException(fhirContext, errors);
    }
  }
}
