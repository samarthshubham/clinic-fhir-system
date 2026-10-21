package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.shubhamforge.clinic.dto.CarePlanRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.CarePlanMapper;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.time.LocalDate;
import org.hl7.fhir.r4.model.Bundle;
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
    CarePlan plan =
        service.createCarePlan(
            new CarePlanRequest("p1", null, "BP control", "active", LocalDate.now(), null));

    assertThat(fhir.created()).containsExactly(plan);
    assertThat(plan.getIdElement().getIdPart()).isEqualTo("1");
    assertThat(plan.getTitle()).isEqualTo("BP control");
  }

  @Test
  void getReturnsStoredPlan() {
    CarePlan stored = new CarePlan();
    fhir.givenStored(stored, "cp1");

    assertThat(service.getCarePlan("cp1")).isSameAs(stored);
  }

  @Test
  void getUnknownPlanThrowsNotFound() {
    assertThatThrownBy(() -> service.getCarePlan("missing"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessage("CarePlan with id 'missing' not found");
  }

  @Test
  void listsPlansForPatient() {
    Bundle plans = bundleOf(new CarePlan());
    fhir.givenSearchResult(CarePlan.class, plans);

    assertThat(service.getCarePlansForPatient("p1")).isSameAs(plans);
    assertThat(fhir.lastSearchCriteria(CarePlan.class)).containsExactly("subject=Patient/p1");
  }

  @Test
  void activePlanIsASingleActivePlan() {
    service.getActiveCarePlanForPatient("p1");

    assertThat(fhir.lastSearchCriteria(CarePlan.class))
        .containsExactly("subject=Patient/p1", "status=active", "_count=1");
  }
}
