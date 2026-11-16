package io.github.samarthshubham.clinic.mapper;

import static io.github.samarthshubham.clinic.support.FhirDates.localDate;
import static io.github.samarthshubham.clinic.support.GoldenFhir.assertMatchesGolden;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.samarthshubham.clinic.client.CrdClient;
import io.github.samarthshubham.clinic.dto.CoverageInformation;
import io.github.samarthshubham.clinic.dto.CrdCard;
import io.github.samarthshubham.clinic.dto.ServiceRequestRequest;
import io.github.samarthshubham.clinic.dto.ServiceRequestResponse;
import io.github.samarthshubham.clinic.support.GoldenFhir;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ServiceRequestMapperTest {

  private static final LocalDate AUTHORED = LocalDate.of(2026, 3, 3);

  private final ServiceRequestMapper mapper = new ServiceRequestMapper();

  @Test
  void mapsOrderFieldsAndReferences() {
    ServiceRequest order =
        mapper.toFhir(
            new ServiceRequestRequest(
                "p1",
                "e1",
                "dr1",
                "Lipid panel",
                null,
                null,
                "laboratory",
                "active",
                "urgent",
                AUTHORED));

    assertThat(order.getSubject().getReference()).isEqualTo("Patient/p1");
    assertThat(order.getEncounter().getReference()).isEqualTo("Encounter/e1");
    assertThat(order.getRequester().getReference()).isEqualTo("Practitioner/dr1");
    assertThat(order.getCode().getText()).isEqualTo("Lipid panel");
    assertThat(order.getCategoryFirstRep().getText()).isEqualTo("laboratory");
    assertThat(order.getStatus()).isEqualTo(ServiceRequest.ServiceRequestStatus.ACTIVE);
    assertThat(order.getPriority()).isEqualTo(ServiceRequest.ServiceRequestPriority.URGENT);
    assertThat(localDate(order.getAuthoredOn())).isEqualTo(AUTHORED);
  }

  @ParameterizedTest
  @CsvSource({
    "draft,routine,DRAFT,ROUTINE",
    "completed,asap,COMPLETED,ASAP",
    "revoked,stat,REVOKED,STAT"
  })
  void mapsEveryStatusAndPriority(
      String status,
      String priority,
      ServiceRequest.ServiceRequestStatus expectedStatus,
      ServiceRequest.ServiceRequestPriority expectedPriority) {
    ServiceRequest order =
        mapper.toFhir(
            new ServiceRequestRequest(
                "p1", null, null, "CBC", null, null, "laboratory", status, priority, AUTHORED));

    assertThat(order.getStatus()).isEqualTo(expectedStatus);
    assertThat(order.getPriority()).isEqualTo(expectedPriority);
  }

  @ParameterizedTest
  @CsvSource({"open,routine", "active,whenever"})
  void unknownStatusOrPriorityIsRejected(String status, String priority) {
    ServiceRequestRequest request =
        new ServiceRequestRequest(
            "p1", null, null, "CBC", null, null, "laboratory", status, priority, AUTHORED);

    assertThatThrownBy(() -> mapper.toFhir(request)).isInstanceOf(FHIRException.class);
  }

  @Test
  void omitsEncounterAndRequesterWhenNotGiven() {
    ServiceRequest order =
        mapper.toFhir(
            new ServiceRequestRequest(
                "p1",
                null,
                null,
                "Chest X-ray",
                null,
                null,
                "imaging",
                "active",
                "routine",
                AUTHORED));

    assertThat(order.hasEncounter()).isFalse();
    assertThat(order.hasRequester()).isFalse();
  }

  @Test
  void toResponseIsTheReverseOfToFhir() {
    ServiceRequest serviceRequest =
        mapper.toFhir(
            new ServiceRequestRequest(
                "p1",
                "e1",
                "dr1",
                "Lipid panel",
                null,
                null,
                "laboratory",
                "active",
                "urgent",
                AUTHORED));
    serviceRequest.setId("sr1");

    assertThat(mapper.toResponse(serviceRequest))
        .isEqualTo(
            new ServiceRequestResponse(
                "sr1",
                "p1",
                "e1",
                "dr1",
                "Lipid panel",
                null,
                null,
                "laboratory",
                "active",
                "urgent",
                AUTHORED,
                null,
                List.of()));
  }

  @Test
  void toResponseLeavesMissingFieldsNull() {
    ServiceRequest serviceRequest = new ServiceRequest();
    serviceRequest.setId("sr1");

    assertThat(mapper.toResponse(serviceRequest))
        .isEqualTo(
            new ServiceRequestResponse(
                "sr1", null, null, null, null, null, null, null, null, null, null, null,
                List.of()));
  }

  @Test
  void setsTheUsCoreProfileOrderIntentAndCoding() {
    ServiceRequest order =
        mapper.toFhir(
            new ServiceRequestRequest(
                "p1",
                null,
                null,
                "Lipid panel",
                "http://loinc.org",
                "57698-3",
                "laboratory",
                "active",
                "routine",
                AUTHORED));

    assertThat(order.getMeta().hasProfile(UsCoreProfiles.SERVICE_REQUEST)).isTrue();
    assertThat(order.getIntent()).isEqualTo(ServiceRequest.ServiceRequestIntent.ORDER);
    assertThat(order.getCode().getText()).isEqualTo("Lipid panel");
    assertThat(order.getCode().getCodingFirstRep().getSystem()).isEqualTo("http://loinc.org");
    assertThat(order.getCode().getCodingFirstRep().getCode()).isEqualTo("57698-3");
    assertThat(order.getAuthoredOnElement().getValueAsString()).isEqualTo("2026-03-03");
    order.setId("sr1");
    assertThat(mapper.toResponse(order))
        .extracting(ServiceRequestResponse::codeSystem, ServiceRequestResponse::code)
        .containsExactly("http://loinc.org", "57698-3");
  }

  @Test
  void omitsTheCodingWhenNoCodeIsGiven() {
    ServiceRequest order =
        mapper.toFhir(
            new ServiceRequestRequest(
                "p1", null, null, "CBC", null, null, "laboratory", "active", "routine", AUTHORED));

    assertThat(order.getCode().hasCoding()).isFalse();
  }

  @Test
  void serviceRequestMatchesTheGoldenFixture() {
    assertMatchesGolden(
        mapper.toFhir(
            new ServiceRequestRequest(
                "seed-priya-nair",
                null,
                "seed-default-practitioner",
                "Complete Blood Count",
                "http://loinc.org",
                "58410-2",
                "laboratory",
                "active",
                "routine",
                AUTHORED)),
        "provider/service-request.json");
  }

  @Test
  void toResponseReadsTheStoredCoverageInformationAndCards() throws Exception {
    ServiceRequest systemAction =
        FhirContext.forR4Cached()
            .newJsonParser()
            .parseResource(
                ServiceRequest.class,
                new ObjectMapper()
                    .readTree(GoldenFhir.read("crd/order-sign-response-pa-needed.json"))
                    .path("systemActions")
                    .path(0)
                    .path("resource")
                    .toString());
    ServiceRequest stored = new ServiceRequest();
    stored.setId("sr1");
    CrdCard card = new CrdCard("Prior authorization needed", "warning", null, "Praxis Health Plan");
    mapper.addCrdResult(
        stored, systemAction.getExtensionsByUrl(CrdClient.COVERAGE_INFORMATION), List.of(card));

    ServiceRequestResponse response = mapper.toResponse(stored);

    assertThat(response.coverage())
        .isEqualTo(
            new CoverageInformation(
                "coverage-sandra-okafor",
                "covered",
                "auth-needed",
                "clinical",
                "Clinical documentation is needed for the prior authorization request",
                "http://praxishealthplan.example/fhir/Questionnaire/mri-lumbar-spine",
                LocalDate.of(2026, 11, 11),
                "65f84693-1eca-398b-b3da-1bec7a3a0125"));
    assertThat(response.cards()).containsExactly(card);
  }
}
