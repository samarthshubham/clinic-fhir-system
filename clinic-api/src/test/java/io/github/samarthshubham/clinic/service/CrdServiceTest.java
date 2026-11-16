package io.github.samarthshubham.clinic.service;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.client.CrdClient;
import io.github.samarthshubham.clinic.client.CrdResult;
import io.github.samarthshubham.clinic.mapper.CoverageMapper;
import io.github.samarthshubham.clinic.mapper.PatientMapper;
import io.github.samarthshubham.clinic.mapper.PractitionerMapper;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrdServiceTest {

  private FhirClientStub fhir;
  private CrdClient crdClient;
  private CrdService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    crdClient = mock(CrdClient.class);
    service =
        new CrdService(
            crdClient,
            new CoverageService(fhir.client(), new CoverageMapper()),
            new PatientService(fhir.client(), new PatientMapper()),
            new PractitionerService(fhir.client(), new PractitionerMapper()));
    fhir.givenStored(new Patient(), "p1");
    fhir.givenStored(new Practitioner(), "dr1");
  }

  private void givenActiveCoverage() {
    Coverage coverage = new Coverage();
    coverage.setId("cov1");
    fhir.givenSearchResultWhen(Coverage.class, bundleOf(coverage), "status=active");
  }

  @Test
  void checkOrderLinksTheActiveCoverageAndCallsOrderSign() {
    givenActiveCoverage();
    CrdResult answer = CrdResult.coverageUnknown();
    when(crdClient.orderSign(
            any(ServiceRequest.class), any(Patient.class), any(Coverage.class), any()))
        .thenReturn(answer);
    ServiceRequest order = new ServiceRequest();

    CrdResult result = service.checkOrder(order, "p1", "dr1");

    assertThat(result).isSameAs(answer);
    assertThat(order.getInsuranceFirstRep().getReference()).isEqualTo("Coverage/cov1");
    assertThat(fhir.lastSearchCriteria(Coverage.class)).contains("beneficiary=Patient/p1");
  }

  @Test
  void checkOrderWithoutCoverageSkipsTheHook() {
    ServiceRequest order = new ServiceRequest();

    assertThat(service.checkOrder(order, "p1", null)).isEqualTo(CrdResult.none());
    assertThat(order.hasInsurance()).isFalse();
    verifyNoInteractions(crdClient);
  }

  @Test
  void checkAppointmentCallsAppointmentBookWithoutAPractitionerWhenNoneIsGiven() {
    givenActiveCoverage();
    when(crdClient.appointmentBook(
            any(Appointment.class), any(Patient.class), any(Coverage.class), isNull()))
        .thenReturn(CrdResult.none());

    assertThat(service.checkAppointment(new Appointment(), "p1", null)).isEqualTo(CrdResult.none());
  }

  @Test
  void checkAppointmentWithoutCoverageSkipsTheHook() {
    assertThat(service.checkAppointment(new Appointment(), "p1", "dr1"))
        .isEqualTo(CrdResult.none());
    verifyNoInteractions(crdClient);
  }
}
