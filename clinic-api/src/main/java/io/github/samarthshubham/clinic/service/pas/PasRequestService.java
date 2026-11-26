package io.github.samarthshubham.clinic.service.pas;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import io.github.samarthshubham.clinic.exception.PasRequestException;
import io.github.samarthshubham.clinic.service.CrdService;
import io.github.samarthshubham.clinic.service.PatientService;
import io.github.samarthshubham.clinic.service.PractitionerService;
import io.github.samarthshubham.clinic.service.ServiceRequestService;
import io.github.samarthshubham.clinic.service.dtr.DtrService;
import io.github.samarthshubham.clinic.service.pas.PasBundleBuilder.Billing;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PasRequestService {

  private final IGenericClient fhirClient;
  private final FhirContext fhirContext;
  private final ServiceRequestService orders;
  private final PatientService patients;
  private final PractitionerService practitioners;
  private final CrdService crd;
  private final DtrService dtr;
  private final PasBundleBuilder builder;
  private final String organizationId;

  public PasRequestService(
      IGenericClient fhirClient,
      FhirContext fhirContext,
      ServiceRequestService orders,
      PatientService patients,
      PractitionerService practitioners,
      CrdService crd,
      DtrService dtr,
      PasBundleBuilder builder,
      @Value("${clinic.default-org-id}") String organizationId) {
    this.fhirClient = fhirClient;
    this.fhirContext = fhirContext;
    this.orders = orders;
    this.patients = patients;
    this.practitioners = practitioners;
    this.crd = crd;
    this.dtr = dtr;
    this.builder = builder;
    this.organizationId = organizationId;
  }

  public Bundle build(String orderId, Billing billing) {
    String baseUrl = fhirClient.getServerBase();
    ServiceRequest order = orders.findServiceRequest(orderId);
    String practitionerId =
        referenceId(
            order.getRequester(),
            "Practitioner",
            baseUrl,
            "The order needs an ordering Practitioner with an NPI");
    String patientId =
        referenceId(order.getSubject(), "Patient", baseUrl, "The order needs a Patient");
    Practitioner practitioner = practitioners.findPractitioner(practitionerId);
    Coverage coverage = crd.activeCoverageWithPayer(patientId);
    if (coverage == null) {
      throw new PasRequestException("The patient needs active coverage with the configured payer");
    }
    String insurerId =
        referenceId(
            coverage.getPayorFirstRep(),
            "Organization",
            baseUrl,
            "The selected coverage needs a payer Organization");
    Patient patient = patients.findPatient(patientId);
    QuestionnaireResponse response = dtr.findSavedResponse(orderId, patientId);
    return builder.build(
        new PasBundleBuilder.Request(
            order,
            patient,
            coverage,
            practitioner,
            organization(organizationId),
            organization(insurerId),
            response,
            documentationAuthors(response, practitionerId, baseUrl)),
        billing,
        baseUrl,
        new DateTimeType(new Date(), TemporalPrecisionEnum.MILLI, TimeZone.getTimeZone("UTC")));
  }

  private Organization organization(String id) {
    try {
      return fhirClient.read().resource(Organization.class).withId(id).execute();
    } catch (ResourceNotFoundException ex) {
      throw new PasRequestException(
          "The PAS request needs both the clinic and payer Organizations");
    }
  }

  private List<Practitioner> documentationAuthors(
      QuestionnaireResponse response, String ordererId, String baseUrl) {
    if (response == null) {
      return List.of();
    }
    return fhirContext
        .newTerser()
        .getAllPopulatedChildElementsOfType(response, Reference.class)
        .stream()
        .map(Reference::getReferenceElement)
        .filter(id -> "Practitioner".equals(id.getResourceType()) && id.hasIdPart())
        .filter(id -> !id.hasBaseUrl() || baseUrl.equals(id.getBaseUrl()))
        .map(id -> id.getIdPart())
        .filter(id -> !ordererId.equals(id))
        .distinct()
        .map(practitioners::findPractitioner)
        .toList();
  }

  private static String referenceId(
      Reference reference, String type, String baseUrl, String error) {
    var id = reference.getReferenceElement();
    if (!type.equals(id.getResourceType())
        || !id.hasIdPart()
        || (id.hasBaseUrl() && !baseUrl.equals(id.getBaseUrl()))) {
      throw new PasRequestException(error);
    }
    return id.getIdPart();
  }
}
