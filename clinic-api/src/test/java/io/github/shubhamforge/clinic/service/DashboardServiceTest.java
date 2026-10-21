package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static io.github.shubhamforge.clinic.support.FhirClientStub.vital;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.shubhamforge.clinic.dto.AppointmentRequest;
import io.github.shubhamforge.clinic.dto.CarePlanRequest;
import io.github.shubhamforge.clinic.dto.DashboardResponse;
import io.github.shubhamforge.clinic.dto.DiagnosticReportRequest;
import io.github.shubhamforge.clinic.dto.EncounterRequest;
import io.github.shubhamforge.clinic.dto.GoalProgress;
import io.github.shubhamforge.clinic.dto.GoalRequest;
import io.github.shubhamforge.clinic.dto.PatientRequest;
import io.github.shubhamforge.clinic.dto.PatientSummary;
import io.github.shubhamforge.clinic.dto.PractitionerRequest;
import io.github.shubhamforge.clinic.dto.ServiceRequestRequest;
import io.github.shubhamforge.clinic.dto.SnapshotResponse;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.AppointmentMapper;
import io.github.shubhamforge.clinic.mapper.CarePlanMapper;
import io.github.shubhamforge.clinic.mapper.DiagnosticReportMapper;
import io.github.shubhamforge.clinic.mapper.EncounterMapper;
import io.github.shubhamforge.clinic.mapper.GoalMapper;
import io.github.shubhamforge.clinic.mapper.PatientMapper;
import io.github.shubhamforge.clinic.mapper.PractitionerMapper;
import io.github.shubhamforge.clinic.mapper.ServiceRequestMapper;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Goal;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

  private static final String ORG_ID = "seed-default-org";
  private static final LocalDate DAY = LocalDate.of(2026, 5, 1);
  private static final String SYSTOLIC = "code=http://loinc.org|8480-6";

  @Mock private PatientService patientService;
  @Mock private SnapshotService snapshotService;
  @Mock private EncounterService encounterService;
  @Mock private AppointmentService appointmentService;
  @Mock private ServiceRequestService serviceRequestService;
  @Mock private DiagnosticReportService diagnosticReportService;
  @Mock private CarePlanService carePlanService;
  @Mock private GoalService goalService;
  @Mock private PractitionerService practitionerService;

  private FhirClientStub fhir;
  private DashboardService service;
  private final SnapshotResponse snapshot =
      new SnapshotResponse(List.of(), List.of(), null, List.of());

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service =
        new DashboardService(
            patientService,
            snapshotService,
            encounterService,
            appointmentService,
            serviceRequestService,
            diagnosticReportService,
            carePlanService,
            goalService,
            practitionerService,
            fhir.client(),
            ORG_ID);
    lenient().when(patientService.getPatient("p1")).thenReturn(patient());
    lenient().when(snapshotService.getSnapshot("p1")).thenReturn(snapshot);
    lenient()
        .when(appointmentService.getUpcomingAppointmentsForPatient("p1"))
        .thenReturn(new Bundle());
    lenient()
        .when(serviceRequestService.getPendingServiceRequestsForPatient("p1"))
        .thenReturn(new Bundle());
    lenient()
        .when(diagnosticReportService.getMostRecentDiagnosticReport("p1"))
        .thenReturn(new Bundle());
    lenient().when(carePlanService.getActiveCarePlanForPatient("p1")).thenReturn(new Bundle());
    lenient().when(practitionerService.getPractitioner("dr1")).thenReturn(house());
    fhir.givenStored(new Organization().setName("Praxis Clinic"), ORG_ID);
  }

  @Test
  void patientSummaryUsesNameBirthDateGenderAndFirstContacts() {
    PatientSummary summary = service.getDashboard("p1").patient();

    assertThat(summary)
        .isEqualTo(
            new PatientSummary(
                "p1", "Priya", "Nair", "1985-03-14", "female", "p1", "+1-555-0100", "p@x.org"));
  }

  @Test
  void unknownPatientFailsTheWholeDashboard() {
    when(patientService.getPatient("missing"))
        .thenThrow(new ResourceNotFoundException("Patient", "missing"));

    assertThatThrownBy(() -> service.getDashboard("missing"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void fullDashboardHasEverySectionAndNoWarnings() {
    givenRecentEncounters(encounter("e1", DAY, "dr1"), encounter("e2", DAY.minusDays(30), null));
    when(appointmentService.getUpcomingAppointmentsForPatient("p1"))
        .thenReturn(bundleOf(appointment()));
    when(serviceRequestService.getPendingServiceRequestsForPatient("p1"))
        .thenReturn(bundleOf(serviceRequest()));
    when(diagnosticReportService.getMostRecentDiagnosticReport("p1"))
        .thenReturn(bundleOf(report()));

    DashboardResponse dashboard = service.getDashboard("p1");

    assertThat(dashboard.warnings()).isEmpty();
    assertThat(dashboard.snapshot()).isSameAs(snapshot);
    assertThat(dashboard.recentEncounters())
        .containsExactly(
            Map.of("id", "e1", "date", "2026-05-01", "reason", "Headache", "status", "finished"),
            Map.of("id", "e2", "date", "2026-04-01", "reason", "Headache", "status", "finished"));
    assertThat(fhir.lastSearchCriteria(Encounter.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", "_sort=-date", "_count=3");
    assertThat(dashboard.careTeam())
        .containsEntry(
            "primaryDoctor",
            Map.of("id", "dr1", "name", "Dr. Gregory House", "specialty", "Diagnostics"))
        .containsEntry("organization", Map.of("id", ORG_ID, "name", "Praxis Clinic"));
    assertThat(dashboard.upcomingAppointment())
        .containsEntry("id", "a1")
        .containsEntry("start", "2026-06-01")
        .containsEntry("description", "Follow-up")
        .containsEntry("status", "booked")
        .containsEntry("provider", "Dr. Gregory House");
    assertThat(dashboard.pendingServiceRequests())
        .singleElement()
        .isEqualTo(
            Map.of(
                "id", "sr1",
                "code", "Lipid panel",
                "category", "laboratory",
                "orderedOn", "2026-04-28",
                "priority", "routine",
                "status", "active",
                "orderedBy", "Dr. Gregory House"));
    assertThat(dashboard.recentDiagnosticReport())
        .containsEntry("id", "r1")
        .containsEntry("title", "Lipid panel")
        .containsEntry("issued", "2026-04-30")
        .containsEntry("conclusion", "LDL elevated")
        .containsEntry("status", "final")
        .containsEntry("serviceRequestId", "sr1");
  }

  @Test
  void primaryDoctorComesFromTheAttenderOfTheMostRecentEncounter() {
    givenRecentEncounters(encounter("e1", DAY, null), encounter("e2", DAY.minusDays(1), "dr1"));

    DashboardResponse dashboard = service.getDashboard("p1");

    assertThat(dashboard.careTeam()).doesNotContainKey("primaryDoctor");
    verify(practitionerService, never()).getPractitioner(anyString());
  }

  @Test
  void unresolvablePractitionerIsAWarningAndLeavesOutProviders() {
    givenRecentEncounters(encounter("e1", DAY, "dr1"));
    when(practitionerService.getPractitioner("dr1"))
        .thenThrow(new ResourceNotFoundException("Practitioner", "dr1"));
    when(appointmentService.getUpcomingAppointmentsForPatient("p1"))
        .thenReturn(bundleOf(appointment()));

    DashboardResponse dashboard = service.getDashboard("p1");

    assertThat(dashboard.warnings()).containsExactly("care-team.practitioner: unavailable");
    assertThat(dashboard.careTeam()).doesNotContainKey("primaryDoctor");
    assertThat(dashboard.upcomingAppointment()).doesNotContainKey("provider");
  }

  @Test
  void missingOrganizationIsOmittedWithoutWarning() {
    fhir.givenReadFails(Organization.class, ORG_ID, new InternalErrorException("down"));

    DashboardResponse dashboard = service.getDashboard("p1");

    assertThat(dashboard.careTeam()).doesNotContainKey("organization");
    assertThat(dashboard.warnings()).isEmpty();
  }

  @Test
  void emptySectionsAreNullOrEmpty() {
    DashboardResponse dashboard = service.getDashboard("p1");

    assertThat(dashboard.recentEncounters()).isEmpty();
    assertThat(dashboard.upcomingAppointment()).isNull();
    assertThat(dashboard.pendingServiceRequests()).isEmpty();
    assertThat(dashboard.recentDiagnosticReport()).isNull();
    assertThat(dashboard.activeCarePlan()).isNull();
    assertThat(dashboard.warnings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "snapshot",
        "encounters",
        "appointments",
        "service-requests",
        "diagnostic-reports",
        "care-plans"
      })
  void aFailingSectionIsReportedWithoutBreakingTheRest(String section) {
    RuntimeException failure = new InternalErrorException("FHIR server error");
    switch (section) {
      case "snapshot" -> when(snapshotService.getSnapshot("p1")).thenThrow(failure);
      case "encounters" -> fhir.givenSearchFails(Encounter.class, failure);
      case "appointments" ->
          when(appointmentService.getUpcomingAppointmentsForPatient("p1")).thenThrow(failure);
      case "service-requests" ->
          when(serviceRequestService.getPendingServiceRequestsForPatient("p1")).thenThrow(failure);
      case "diagnostic-reports" ->
          when(diagnosticReportService.getMostRecentDiagnosticReport("p1")).thenThrow(failure);
      default -> when(carePlanService.getActiveCarePlanForPatient("p1")).thenThrow(failure);
    }

    DashboardResponse dashboard = service.getDashboard("p1");

    assertThat(dashboard.warnings()).containsExactly(section + ": unavailable");
    assertThat(dashboard.patient().id()).isEqualTo("p1");
    assertThat(dashboard.careTeam()).containsKey("organization");
    if (!"snapshot".equals(section)) {
      assertThat(dashboard.snapshot()).isSameAs(snapshot);
    }
  }

  @Test
  void carePlanGoalShowsProgressAgainstLatestAndBaselineReadings() {
    givenCarePlanWithGoals("g1");
    when(goalService.getGoal("g1")).thenReturn(bpGoal("g1"));
    givenSystolicReadings(140, 160);

    Map<String, Object> plan = service.getDashboard("p1").activeCarePlan();

    assertThat(plan)
        .containsEntry("id", "cp1")
        .containsEntry("title", "BP control")
        .containsEntry("status", "active");
    Map<String, Object> goal = goals(plan).get(0);
    assertThat(goal)
        .containsEntry("id", "g1")
        .containsEntry("description", "Lower systolic BP")
        .containsEntry("status", "active")
        .containsEntry("targetValue", 130.0)
        .containsEntry("targetDate", "2026-09-30")
        .containsEntry("progress", new GoalProgress(140.0, 130.0, false, 93, null, 160.0));
    assertThat(fhir.searches(Observation.class))
        .containsExactlyInAnyOrder(
            List.of("subject=Patient/p1", SYSTOLIC, "_sort=-date", "_count=1"),
            List.of("subject=Patient/p1", SYSTOLIC, "_sort=date", "_count=1"));
  }

  @Test
  void goalIsOnTrackAndCappedAtOneHundredPercentOnceReached() {
    givenCarePlanWithGoals("g1");
    when(goalService.getGoal("g1")).thenReturn(bpGoal("g1"));
    givenSystolicReadings(120, 160);

    GoalProgress progress =
        (GoalProgress) goals(service.getDashboard("p1").activeCarePlan()).get(0).get("progress");

    assertThat(progress.onTrack()).isTrue();
    assertThat(progress.percentToGoal()).isEqualTo(100);
  }

  @Test
  void goalReadingExactlyAtTargetIsOnTrack() {
    givenCarePlanWithGoals("g1");
    when(goalService.getGoal("g1")).thenReturn(bpGoal("g1"));
    givenSystolicReadings(130, 160);

    GoalProgress progress =
        (GoalProgress) goals(service.getDashboard("p1").activeCarePlan()).get(0).get("progress");

    assertThat(progress.onTrack()).isTrue();
    assertThat(progress.percentToGoal()).isEqualTo(100);
  }

  @Test
  void goalWithoutReadingsSaysSo() {
    givenCarePlanWithGoals("g1");
    when(goalService.getGoal("g1")).thenReturn(bpGoal("g1"));

    GoalProgress progress =
        (GoalProgress) goals(service.getDashboard("p1").activeCarePlan()).get(0).get("progress");

    assertThat(progress)
        .isEqualTo(
            new GoalProgress(null, 130.0, null, null, "No matching measurements found", null));
  }

  @Test
  void failedProgressSearchIsReportedOnTheGoal() {
    givenCarePlanWithGoals("g1");
    when(goalService.getGoal("g1")).thenReturn(bpGoal("g1"));
    fhir.givenSearchFails(Observation.class, new InternalErrorException("down"));

    GoalProgress progress =
        (GoalProgress) goals(service.getDashboard("p1").activeCarePlan()).get(0).get("progress");

    assertThat(progress.message()).isEqualTo("Progress evaluation failed");
  }

  @Test
  void goalWithoutTargetHasNoProgress() {
    givenCarePlanWithGoals("g1");
    Goal goal =
        new GoalMapper()
            .toFhir(new GoalRequest("p1", "Walk daily", "active", null, null, null, null, null));
    goal.setId("g1");
    when(goalService.getGoal("g1")).thenReturn(goal);

    Map<String, Object> result = goals(service.getDashboard("p1").activeCarePlan()).get(0);

    assertThat(result).containsEntry("description", "Walk daily").doesNotContainKey("progress");
    assertThat(fhir.searchCount(Observation.class)).isZero();
  }

  @Test
  void unresolvableGoalIsSkippedWithAWarning() {
    givenCarePlanWithGoals("g1", "g9");
    when(goalService.getGoal("g1")).thenReturn(bpGoal("g1"));
    when(goalService.getGoal("g9")).thenThrow(new ResourceNotFoundException("Goal", "g9"));

    DashboardResponse dashboard = service.getDashboard("p1");

    assertThat(goals(dashboard.activeCarePlan()))
        .extracting(g -> g.get("id"))
        .containsExactly("g1");
    assertThat(dashboard.warnings()).containsExactly("care-plan.goal.g9: unavailable");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> goals(Map<String, Object> plan) {
    return (List<Map<String, Object>>) plan.get("goals");
  }

  private void givenRecentEncounters(Encounter... encounters) {
    fhir.givenSearchResult(Encounter.class, bundleOf(encounters));
  }

  private void givenCarePlanWithGoals(String... goalIds) {
    CarePlan plan =
        new CarePlanMapper()
            .toFhir(new CarePlanRequest("p1", null, "BP control", "active", DAY, List.of(goalIds)));
    plan.setId("cp1");
    when(carePlanService.getActiveCarePlanForPatient("p1")).thenReturn(bundleOf(plan));
  }

  private void givenSystolicReadings(double latest, double earliest) {
    fhir.givenSearchResultWhen(
        Observation.class, bundleOf(vital("8480-6", latest, DAY)), SYSTOLIC, "_sort=-date");
    fhir.givenSearchResultWhen(
        Observation.class,
        bundleOf(vital("8480-6", earliest, DAY.minusDays(60))),
        SYSTOLIC,
        "_sort=date");
  }

  private static Patient patient() {
    Patient patient =
        new PatientMapper()
            .toFhir(
                new PatientRequest(
                    "Priya",
                    "Nair",
                    LocalDate.of(1985, 3, 14),
                    "female",
                    "+1-555-0100",
                    "p@x.org"));
    patient.setId("p1");
    return patient;
  }

  private static Practitioner house() {
    Practitioner practitioner =
        new PractitionerMapper()
            .toFhir(new PractitionerRequest("Gregory", "House", "Diagnostics", null, null));
    practitioner.setId("dr1");
    return practitioner;
  }

  private static Encounter encounter(String id, LocalDate date, String practitionerId) {
    Encounter encounter =
        new EncounterMapper()
            .toFhir(new EncounterRequest("p1", date, "Headache", "finished", practitionerId, null));
    encounter.setId(id);
    return encounter;
  }

  private static Appointment appointment() {
    Appointment appointment =
        new AppointmentMapper()
            .toFhir(
                new AppointmentRequest(
                    "p1",
                    null,
                    LocalDate.of(2026, 6, 1).atTime(9, 0),
                    LocalDate.of(2026, 6, 1).atTime(9, 30),
                    "Follow-up",
                    "booked"));
    appointment.setId("a1");
    return appointment;
  }

  private static ServiceRequest serviceRequest() {
    ServiceRequest request =
        new ServiceRequestMapper()
            .toFhir(
                new ServiceRequestRequest(
                    "p1",
                    null,
                    null,
                    "Lipid panel",
                    "laboratory",
                    "active",
                    "routine",
                    LocalDate.of(2026, 4, 28)));
    request.setId("sr1");
    return request;
  }

  private static DiagnosticReport report() {
    DiagnosticReport report =
        new DiagnosticReportMapper()
            .toFhir(
                new DiagnosticReportRequest(
                    "p1",
                    null,
                    "sr1",
                    "Lipid panel",
                    "final",
                    LocalDate.of(2026, 4, 30),
                    "LDL elevated",
                    null));
    report.setId("r1");
    return report;
  }

  private static Goal bpGoal(String id) {
    Goal goal =
        new GoalMapper()
            .toFhir(
                new GoalRequest(
                    "p1",
                    "Lower systolic BP",
                    "active",
                    "8480-6",
                    "Systolic blood pressure",
                    130.0,
                    "mm[Hg]",
                    LocalDate.of(2026, 9, 30)));
    goal.setId(id);
    return goal;
  }
}
