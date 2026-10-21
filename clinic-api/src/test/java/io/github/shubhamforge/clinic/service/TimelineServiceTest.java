package io.github.shubhamforge.clinic.service;

import static io.github.shubhamforge.clinic.support.FhirClientStub.bundleOf;
import static io.github.shubhamforge.clinic.support.FhirClientStub.vital;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.shubhamforge.clinic.config.ClinicalThresholds;
import io.github.shubhamforge.clinic.dto.AppointmentRequest;
import io.github.shubhamforge.clinic.dto.DiagnosticReportRequest;
import io.github.shubhamforge.clinic.dto.EncounterRequest;
import io.github.shubhamforge.clinic.dto.GroupedObservation;
import io.github.shubhamforge.clinic.dto.PractitionerRequest;
import io.github.shubhamforge.clinic.dto.ServiceRequestRequest;
import io.github.shubhamforge.clinic.dto.TimelineEvent;
import io.github.shubhamforge.clinic.dto.VitalsRequest;
import io.github.shubhamforge.clinic.exception.ResourceNotFoundException;
import io.github.shubhamforge.clinic.mapper.AppointmentMapper;
import io.github.shubhamforge.clinic.mapper.DiagnosticReportMapper;
import io.github.shubhamforge.clinic.mapper.EncounterMapper;
import io.github.shubhamforge.clinic.mapper.ObservationMapper;
import io.github.shubhamforge.clinic.mapper.PractitionerMapper;
import io.github.shubhamforge.clinic.mapper.ServiceRequestMapper;
import io.github.shubhamforge.clinic.support.FhirClientStub;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimelineServiceTest {

  private static final LocalDate TODAY = LocalDate.now();

  @Mock private PractitionerService practitionerService;

  private FhirClientStub fhir;
  private TimelineService service;

  @BeforeEach
  void setUp() {
    fhir = new FhirClientStub();
    service =
        new TimelineService(
            fhir.client(),
            practitionerService,
            new ClinicalThresholds(140, 180, 90, 120, 95.0, 90.0));
    Practitioner house =
        new PractitionerMapper()
            .toFhir(new PractitionerRequest("Gregory", "House", "Diagnostics", null, null));
    lenient().when(practitionerService.getPractitioner("dr1")).thenReturn(house);
  }

  @Test
  void eventsOfAllTypesAreMergedNewestFirst() {
    givenEncounters(encounter("e1", TODAY.minusDays(10), null));
    givenReports(report("r1", TODAY.minusDays(5), null));
    givenServiceRequests(serviceRequest("sr1", TODAY.minusDays(7), null));
    givenAppointments(appointment("a1", TODAY.plusDays(3), null));

    List<TimelineEvent> timeline = service.getTimeline("p1", null, null, null);

    assertThat(timeline)
        .extracting(TimelineEvent::type, TimelineEvent::id)
        .containsExactly(
            tuple("appointment", "a1"),
            tuple("report", "r1"),
            tuple("service-request", "sr1"),
            tuple("encounter", "e1"));
  }

  @ParameterizedTest(name = "limit {0} -> {1}")
  @CsvSource(
      nullValues = "none",
      value = {"none,20", "0,20", "-5,20", "10,10", "50,50", "100,50"})
  void eachSourceIsQueriedWithTheResolvedLimit(Integer limit, int expected) {
    service.getTimeline("p1", limit, null, null);

    for (Class<? extends Resource> type :
        List.of(Encounter.class, DiagnosticReport.class, ServiceRequest.class, Appointment.class)) {
      assertThat(fhir.lastSearchCriteria(type)).contains("_count=" + expected);
    }
  }

  @Test
  void limitAppliesToTheMergedTimeline() {
    givenEncounters(encounter("e1", TODAY.minusDays(10), null));
    givenReports(report("r1", TODAY.minusDays(5), null));
    givenServiceRequests(serviceRequest("sr1", TODAY.minusDays(7), null));

    assertThat(service.getTimeline("p1", 2, null, null))
        .extracting(TimelineEvent::id)
        .containsExactly("r1", "sr1");
  }

  @Test
  void sourcesAreQueriedNewestFirstForThisPatient() {
    service.getTimeline("p1", null, null, null);

    assertThat(fhir.lastSearchCriteria(Encounter.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", "_sort=-date", "_count=20");
    assertThat(fhir.lastSearchCriteria(DiagnosticReport.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", "_sort=-issued", "_count=20");
    assertThat(fhir.lastSearchCriteria(ServiceRequest.class))
        .containsExactlyInAnyOrder("subject=Patient/p1", "_sort=-authored", "_count=20");
    assertThat(fhir.lastSearchCriteria(Appointment.class))
        .containsExactlyInAnyOrder(
            "actor=Patient/p1", "date=ge" + LocalDate.now(), "_sort=date", "_count=20");
  }

  @Test
  void typesFilterLimitsWhichSourcesAreQueried() {
    service.getTimeline("p1", null, null, "encounter, report");

    assertThat(fhir.searchCount(Encounter.class)).isEqualTo(1);
    assertThat(fhir.searchCount(DiagnosticReport.class)).isEqualTo(1);
    assertThat(fhir.searchCount(ServiceRequest.class)).isZero();
    assertThat(fhir.searchCount(Appointment.class)).isZero();
  }

  @Test
  void beforeCursorIsSentToEachPastSourceAndFiltersTheResult() {
    String cursor = TODAY.minusDays(6).toString();
    givenEncounters(encounter("e1", TODAY.minusDays(10), null));
    givenReports(report("r1", TODAY.minusDays(5), null));
    givenServiceRequests(serviceRequest("sr1", TODAY.minusDays(6), null));
    givenAppointments(appointment("a1", TODAY.plusDays(3), null));

    List<TimelineEvent> timeline = service.getTimeline("p1", null, cursor, null);

    assertThat(timeline).extracting(TimelineEvent::id).containsExactly("e1");
    assertThat(fhir.lastSearchCriteria(Encounter.class)).contains("date=lt" + cursor);
    assertThat(fhir.lastSearchCriteria(DiagnosticReport.class)).contains("issued=lt" + cursor);
    assertThat(fhir.lastSearchCriteria(ServiceRequest.class)).contains("authored=lt" + cursor);
  }

  @Test
  void encounterEventCarriesVisitDetailsVitalsAndProvider() {
    givenEncounters(encounter("e1", TODAY.minusDays(2), "dr1"));
    List<Observation> vitals =
        new ObservationMapper()
            .toFhirObservations(
                new VitalsRequest("p1", "e1", TODAY, 150, 85, null, 93.0, null, null));
    Observation unknownCode = vital("1234-5", 1, TODAY);
    Observation textValue = vital("8867-4", 0, TODAY).setValue(new StringType("n/a"));
    List<Resource> grouped = new ArrayList<>(vitals);
    grouped.add(unknownCode);
    grouped.add(textValue);
    fhir.givenSearchResultWhen(
        Observation.class, bundleOf(grouped.toArray(Resource[]::new)), "encounter=Encounter/e1");

    TimelineEvent event = service.getTimeline("p1", null, null, "encounter").get(0);

    assertThat(event.title()).isEqualTo("Clinic Visit");
    assertThat(event.date()).isEqualTo(TODAY.minusDays(2).toString());
    assertThat(event.subtitle()).isEqualTo("Headache");
    assertThat(event.chiefComplaint()).isEqualTo("Headache");
    assertThat(event.note()).isEqualTo("Rest advised");
    assertThat(event.status()).isEqualTo("finished");
    assertThat(event.resourceId()).isEqualTo("Encounter/e1");
    assertThat(event.metadata()).containsEntry("provider", "Dr. Gregory House");
    assertThat(event.metadata()).doesNotContainKey("_practitionerId");
    assertThat(event.groupedObservations())
        .containsExactly(
            new GroupedObservation("systolic", "Systolic BP", 150.0, "mm[Hg]", true),
            new GroupedObservation("diastolic", "Diastolic BP", 85.0, "mm[Hg]", false),
            new GroupedObservation("spo2", "SpO₂", 93.0, "%", true));
  }

  @Test
  void reportEventLinksToItsOrder() {
    givenReports(report("r1", TODAY.minusDays(1), "sr1"));

    TimelineEvent event = service.getTimeline("p1", null, null, "report").get(0);

    assertThat(event.title()).isEqualTo("Lab Results Available");
    assertThat(event.subtitle()).isEqualTo("Lipid panel");
    assertThat(event.status()).isEqualTo("final");
    assertThat(event.resourceId()).isEqualTo("DiagnosticReport/r1");
    assertThat(event.linkedTo()).isEqualTo("sr1");
    assertThat(event.metadata())
        .containsEntry("conclusion", "LDL elevated")
        .containsEntry("serviceRequestId", "sr1");
  }

  @Test
  void serviceRequestEventShowsCategoryPriorityAndOrderingDoctor() {
    givenServiceRequests(serviceRequest("sr1", TODAY.minusDays(1), "dr1"));

    TimelineEvent event = service.getTimeline("p1", null, null, "service-request").get(0);

    assertThat(event.title()).isEqualTo("Lab Test Ordered");
    assertThat(event.subtitle()).isEqualTo("Lipid panel");
    assertThat(event.metadata())
        .containsEntry("category", "laboratory")
        .containsEntry("priority", "routine")
        .containsEntry("orderedBy", "Dr. Gregory House");
  }

  @Test
  void appointmentEventShowsDescriptionAndProvider() {
    givenAppointments(appointment("a1", TODAY.plusDays(3), "dr1"));

    TimelineEvent event = service.getTimeline("p1", null, null, "appointment").get(0);

    assertThat(event.title()).isEqualTo("Upcoming Appointment");
    assertThat(event.subtitle()).isEqualTo("Follow-up");
    assertThat(event.status()).isEqualTo("booked");
    assertThat(event.metadata()).containsEntry("provider", "Dr. Gregory House");
  }

  @Test
  void eachPractitionerIsLookedUpOnceAndUnknownOnesFallBackToTheirId() {
    when(practitionerService.getPractitioner("ghost"))
        .thenThrow(new ResourceNotFoundException("Practitioner", "ghost"));
    givenEncounters(
        encounter("e1", TODAY.minusDays(2), "dr1"), encounter("e2", TODAY.minusDays(3), "ghost"));
    givenServiceRequests(serviceRequest("sr1", TODAY.minusDays(1), "dr1"));

    List<TimelineEvent> timeline = service.getTimeline("p1", null, null, null);

    assertThat(timeline)
        .extracting(
            e -> e.id(), e -> e.metadata().getOrDefault("provider", e.metadata().get("orderedBy")))
        .containsExactly(
            tuple("sr1", "Dr. Gregory House"),
            tuple("e1", "Dr. Gregory House"),
            tuple("e2", "ghost"));
    verify(practitionerService, times(1)).getPractitioner("dr1");
    verify(practitionerService, times(2)).getPractitioner(anyString());
  }

  @Test
  void eventsWithoutADateAreSkipped() {
    Encounter undated = encounter("e1", TODAY, null).setPeriod(null);
    givenEncounters(undated, encounter("e2", TODAY.minusDays(1), null));

    assertThat(service.getTimeline("p1", null, null, "encounter"))
        .extracting(TimelineEvent::id)
        .containsExactly("e2");
  }

  private void givenEncounters(Encounter... encounters) {
    fhir.givenSearchResult(Encounter.class, bundleOf(encounters));
  }

  private void givenReports(DiagnosticReport... reports) {
    fhir.givenSearchResult(DiagnosticReport.class, bundleOf(reports));
  }

  private void givenServiceRequests(ServiceRequest... requests) {
    fhir.givenSearchResult(ServiceRequest.class, bundleOf(requests));
  }

  private void givenAppointments(Appointment... appointments) {
    fhir.givenSearchResult(Appointment.class, bundleOf(appointments));
  }

  private static Encounter encounter(String id, LocalDate date, String practitionerId) {
    Encounter encounter =
        new EncounterMapper()
            .toFhir(
                new EncounterRequest(
                    "p1", date, "Headache", "finished", practitionerId, "Rest advised"));
    encounter.setId(id);
    return encounter;
  }

  private static DiagnosticReport report(String id, LocalDate issued, String serviceRequestId) {
    DiagnosticReport report =
        new DiagnosticReportMapper()
            .toFhir(
                new DiagnosticReportRequest(
                    "p1",
                    null,
                    serviceRequestId,
                    "Lipid panel",
                    "final",
                    issued,
                    "LDL elevated",
                    null));
    report.setId(id);
    return report;
  }

  private static ServiceRequest serviceRequest(
      String id, LocalDate authored, String practitionerId) {
    ServiceRequest request =
        new ServiceRequestMapper()
            .toFhir(
                new ServiceRequestRequest(
                    "p1",
                    null,
                    practitionerId,
                    "Lipid panel",
                    "laboratory",
                    "active",
                    "routine",
                    authored));
    request.setId(id);
    return request;
  }

  private static Appointment appointment(String id, LocalDate day, String practitionerId) {
    Appointment appointment =
        new AppointmentMapper()
            .toFhir(
                new AppointmentRequest(
                    "p1",
                    practitionerId,
                    day.atTime(9, 0),
                    day.atTime(9, 30),
                    "Follow-up",
                    "booked"));
    appointment.setId(id);
    return appointment;
  }
}
