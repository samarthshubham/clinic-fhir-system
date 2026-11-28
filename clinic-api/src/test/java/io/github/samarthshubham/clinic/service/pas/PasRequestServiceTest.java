package io.github.samarthshubham.clinic.service.pas;

import static io.github.samarthshubham.clinic.service.pas.PasBundleBuilderTest.billing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import io.github.samarthshubham.clinic.exception.PasRequestException;
import io.github.samarthshubham.clinic.service.CrdService;
import io.github.samarthshubham.clinic.service.PatientService;
import io.github.samarthshubham.clinic.service.PractitionerService;
import io.github.samarthshubham.clinic.service.ServiceRequestService;
import io.github.samarthshubham.clinic.service.dtr.DtrService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasRequestServiceTest {

  private static final String BASE = "http://praxis.clinic/fhir";
  private final IGenericClient fhirClient = mock(IGenericClient.class, RETURNS_DEEP_STUBS);
  private final ServiceRequestService orders = mock(ServiceRequestService.class);
  private final PatientService patients = mock(PatientService.class);
  private final PractitionerService practitioners = mock(PractitionerService.class);
  private final CrdService crd = mock(CrdService.class);
  private final DtrService dtr = mock(DtrService.class);
  private final PasBundleBuilder.Request input = PasBundleBuilderTest.mri();
  private final String orderId = input.order().getIdPart();
  private final String patientId = input.patient().getIdPart();
  private final String requestorId = input.requestor().getIdPart();
  private final String insurerId = input.insurer().getIdPart();
  private final FhirContext fhir = FhirContext.forR4Cached();
  private final PasRequestService service =
      new PasRequestService(
          fhirClient,
          fhir,
          orders,
          patients,
          practitioners,
          crd,
          dtr,
          new PasBundleBuilder(fhir),
          requestorId);

  @BeforeEach
  void chart() {
    String practitionerId = input.practitioner().getIdPart();
    when(fhirClient.getServerBase()).thenReturn(BASE);
    when(orders.findServiceRequest(orderId)).thenReturn(input.order());
    when(patients.findPatient(patientId)).thenReturn(input.patient());
    when(practitioners.findPractitioner(practitionerId)).thenReturn(input.practitioner());
    when(crd.activeCoverageWithPayer(patientId)).thenReturn(input.coverage());
    when(dtr.findSavedResponse(orderId, patientId)).thenReturn(input.response());
    when(fhirClient.read().resource(Organization.class).withId(requestorId).execute())
        .thenReturn(input.requestor());
    when(fhirClient.read().resource(Organization.class).withId(insurerId).execute())
        .thenReturn(input.insurer());
  }

  @Test
  void includesTheActualDocumentationAuthorWhenDifferentFromTheOrderer() {
    Practitioner author = new Practitioner();
    author.setId("doc-author");
    author.addName().setFamily("Author").addGiven("Alex");
    author
        .addIdentifier()
        .setSystem("http://praxis.clinic/fhir/identifier/username")
        .setValue("staff-user");
    input.response().setAuthor(new Reference("Practitioner/doc-author"));
    when(practitioners.findPractitioner("doc-author")).thenReturn(author);

    Bundle bundle = service.build(orderId, billing());

    assertThat(bundle.getEntry())
        .anySatisfy(
            entry -> {
              assertThat(entry.getFullUrl()).isEqualTo(BASE + "/Practitioner/doc-author");
              Practitioner wireAuthor = (Practitioner) entry.getResource();
              assertThat(wireAuthor.getIdentifierFirstRep().getValue()).isEqualTo("doc-author");
            });
    assertThat(bundle.getEntry())
        .anySatisfy(
            entry -> {
              assertThat(entry.getResource()).isInstanceOf(QuestionnaireResponse.class);
              assertThat(((QuestionnaireResponse) entry.getResource()).getAuthor().getReference())
                  .isEqualTo(BASE + "/Practitioner/doc-author");
            });
  }

  @Test
  void requiresTheOrderToIdentifyAPractitioner() {
    input.order().setRequester(null);
    assertThatThrownBy(() -> service.build(orderId, billing()))
        .isInstanceOf(PasRequestException.class)
        .hasMessageContaining("ordering Practitioner with an NPI");
  }

  @Test
  void rejectsSubmissionWhenCoverageIsForAnotherPayer() {
    when(crd.activeCoverageWithPayer(patientId)).thenReturn(null);
    assertThatThrownBy(() -> service.build(orderId, billing()))
        .isInstanceOf(PasRequestException.class)
        .hasMessageContaining("active coverage with the configured payer");
  }

  @Test
  void reportsMissingOrganization() {
    when(fhirClient.read().resource(Organization.class).withId(requestorId).execute())
        .thenThrow(new ResourceNotFoundException("Organization/" + requestorId));

    assertThatThrownBy(() -> service.build(orderId, billing()))
        .isInstanceOf(PasRequestException.class)
        .hasMessageContaining("both the clinic and payer Organizations");
  }

  @Test
  void requiresAnNpiForTheSubmittingClinic() {
    input.requestor().getIdentifier().clear();

    assertThatThrownBy(() -> service.build(orderId, billing()))
        .isInstanceOf(PasRequestException.class)
        .hasMessageContaining("submitting clinic must have an NPI");
  }
}
