package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.clinic.dto.CarePlanRequest;
import io.github.samarthshubham.clinic.dto.CarePlanResponse;
import io.github.samarthshubham.clinic.exception.ResourceNotFoundException;
import io.github.samarthshubham.clinic.mapper.CarePlanMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.CarePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CarePlanServiceTest {

  private FhirClientStub fhir;
  private CarePlanService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service = new CarePlanService(fhir.client(), new CarePlanMapper());
  }

  @Test
  void createStoresMappedPlanAndReturnsTheStoredCopy() {
    CarePlanResponse plan =
        service.createCarePlan(
            new CarePlanRequest("p1", null, "BP control", "active", LocalDate.now(), null));

    assertThat(fhir.created()).singleElement().isInstanceOf(CarePlan.class);
    assertThat(plan)
        .isEqualTo(
            new CarePlanResponse(
                "1", "p1", List.of(), "BP control", "active", LocalDate.now(), List.of()));
  }

  @Test
  void getReturnsStoredPlan() {
    CarePlan stored = new CarePlan();
    fhir.givenStored(stored, "cp1");

    assertThat(service.getCarePlan("cp1").getIdElement().getIdPart()).isEqualTo("cp1");
  }

  @Test
  void getUnknownPlanThrowsNotFound() {
    assertThatThrownBy(() -> service.getCarePlan("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("CarePlan with id 'missing' not found");
  }

  @Test
  void getPropagatesOtherServerErrors() {
    fhir.givenReadFails(CarePlan.class, "x", new InternalErrorException("FHIR server error"));

    assertThatThrownBy(() -> service.getCarePlan("x")).isInstanceOf(InternalErrorException.class);
  }

  @Test
  void listsPlansForPatient() {
    CarePlan stored = new CarePlan();
    stored.setId("cp1");
    fhir.givenSearchResult(CarePlan.class, bundleOf(stored));

    assertThat(service.getCarePlansForPatient("p1").items())
        .extracting(CarePlanResponse::id)
        .containsExactly("cp1");
    assertThat(fhir.lastSearchCriteria(CarePlan.class))
        .containsExactlyInAnyOrder("subject=Patient/p1");
  }

  @Test
  void activePlanIsASingleActivePlan() {
    service.getActiveCarePlanForPatient("p1");

    assertThat(fhir.lastSearchCriteria(CarePlan.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", "status=active", "_count=1");
  }
}
