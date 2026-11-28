package io.github.samarthshubham.payer.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;

final class PasBundleValidation {

  private static final String CRD_COVERAGE_SNAPSHOT =
      "http://hl7.org/fhir/us/davinci-crd/StructureDefinition/ext-coverage-information|2.2.1";

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
    OperationOutcome errors = validationErrors(fhirClient, input);
    // HAPI 8.12 can fail to evaluate the official CRD slices the first time it uses the snapshot.
    if (errors.hasIssue()
        && errors.getIssue().stream().allMatch(PasBundleValidation::coldSnapshot)) {
      errors = validationErrors(fhirClient, input);
    }
    if (errors.hasIssue()) {
      throw new UnprocessableEntityException(fhirContext, errors);
    }
  }

  private static OperationOutcome validationErrors(IGenericClient fhirClient, Parameters input) {
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
    return errors;
  }

  private static boolean coldSnapshot(OperationOutcome.OperationOutcomeIssueComponent issue) {
    return issue.getCode() == OperationOutcome.IssueType.PROCESSING
        && issue.hasDiagnostics()
        && issue
            .getDiagnostics()
            .startsWith(
                "Slicing cannot be evaluated: Could not match discriminator (url) for slice Extension.extension:")
        && issue
            .getDiagnostics()
            .endsWith(
                " in profile "
                    + CRD_COVERAGE_SNAPSHOT
                    + " - the discriminator [url] does not have fixed value, binding or existence assertions");
  }
}
