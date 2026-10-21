package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import io.github.samarthshubham.clinic.dto.DashboardResponse;
import io.github.samarthshubham.clinic.dto.GoalProgress;
import io.github.samarthshubham.clinic.dto.PatientSummary;
import io.github.samarthshubham.clinic.dto.SnapshotResponse;
import io.github.samarthshubham.clinic.mapper.EncounterMapper;
import io.github.samarthshubham.clinic.mapper.PatientMapper;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
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
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DashboardService {

  private static final String LOINC_SYSTEM = "http://loinc.org";

  private final PatientService patientService;
  private final SnapshotService snapshotService;
  private final EncounterService encounterService;
  private final AppointmentService appointmentService;
  private final ServiceRequestService serviceRequestService;
  private final DiagnosticReportService diagnosticReportService;
  private final CarePlanService carePlanService;
  private final GoalService goalService;
  private final PractitionerService practitionerService;
  private final PatientMapper patientMapper;
  private final EncounterMapper encounterMapper;
  private final IGenericClient fhirClient;
  private final String defaultOrgId;

  public DashboardService(
      PatientService patientService,
      SnapshotService snapshotService,
      EncounterService encounterService,
      AppointmentService appointmentService,
      ServiceRequestService serviceRequestService,
      DiagnosticReportService diagnosticReportService,
      CarePlanService carePlanService,
      GoalService goalService,
      PractitionerService practitionerService,
      PatientMapper patientMapper,
      EncounterMapper encounterMapper,
      IGenericClient fhirClient,
      @Value("${clinic.default-org-id}") String defaultOrgId) {
    this.patientService = patientService;
    this.snapshotService = snapshotService;
    this.encounterService = encounterService;
    this.appointmentService = appointmentService;
    this.serviceRequestService = serviceRequestService;
    this.diagnosticReportService = diagnosticReportService;
    this.carePlanService = carePlanService;
    this.goalService = goalService;
    this.practitionerService = practitionerService;
    this.patientMapper = patientMapper;
    this.encounterMapper = encounterMapper;
    this.fhirClient = fhirClient;
    this.defaultOrgId = defaultOrgId;
  }

  public DashboardResponse getDashboard(String patientId) {
    List<String> warnings = new ArrayList<>();

    Patient patient = patientService.findPatient(patientId);
    PatientSummary patientMap = patientMapper.toSummary(patient);

    SnapshotResponse snapshot = null;
    try {
      snapshot = snapshotService.getSnapshot(patientId);
    } catch (Exception e) {
      log.warn("snapshot: failed for patient {} — {}", patientId, e.getMessage());
      warnings.add("snapshot: unavailable");
    }

    List<Map<String, Object>> recentEncounters = List.of();
    String attendingPractitionerId = null;
    try {
      Bundle encBundle =
          fhirClient
              .search()
              .forResource(Encounter.class)
              .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
              .sort()
              .descending("date")
              .count(3)
              .returnBundle(Bundle.class)
              .execute();
      recentEncounters = buildRecentEncounters(encBundle);
      attendingPractitionerId = extractFirstAttendingId(encBundle);
    } catch (Exception e) {
      log.warn("encounters: failed for patient {} — {}", patientId, e.getMessage());
      warnings.add("encounters: unavailable");
    }

    Map<String, Object> careTeam = buildCareTeam(attendingPractitionerId, warnings);

    Map<String, Object> upcomingAppt = null;
    try {
      Bundle apptBundle = appointmentService.getUpcomingAppointmentsForPatient(patientId);
      upcomingAppt = buildUpcomingAppointment(apptBundle, careTeam);
    } catch (Exception e) {
      log.warn("appointments: failed for patient {} — {}", patientId, e.getMessage());
      warnings.add("appointments: unavailable");
    }

    List<Map<String, Object>> pendingRequests = List.of();
    try {
      Bundle srBundle = serviceRequestService.getPendingServiceRequestsForPatient(patientId);
      pendingRequests = buildPendingServiceRequests(srBundle, careTeam);
    } catch (Exception e) {
      log.warn("service-requests: failed for patient {} — {}", patientId, e.getMessage());
      warnings.add("service-requests: unavailable");
    }

    Map<String, Object> recentReport = null;
    try {
      Bundle rptBundle = diagnosticReportService.getMostRecentDiagnosticReport(patientId);
      if (rptBundle.getEntry() != null && !rptBundle.getEntry().isEmpty()) {
        recentReport =
            buildRecentReport((DiagnosticReport) rptBundle.getEntryFirstRep().getResource());
      }
    } catch (Exception e) {
      log.warn("diagnostic-reports: failed for patient {} — {}", patientId, e.getMessage());
      warnings.add("diagnostic-reports: unavailable");
    }

    Map<String, Object> activeCarePlan = null;
    try {
      Bundle cpBundle = carePlanService.getActiveCarePlanForPatient(patientId);
      if (cpBundle.getEntry() != null && !cpBundle.getEntry().isEmpty()) {
        CarePlan cp = (CarePlan) cpBundle.getEntryFirstRep().getResource();
        activeCarePlan = buildCarePlan(cp, patientId, warnings);
      }
    } catch (Exception e) {
      log.warn("care-plans: failed for patient {} — {}", patientId, e.getMessage());
      warnings.add("care-plans: unavailable");
    }

    return new DashboardResponse(
        patientMap,
        careTeam,
        snapshot,
        upcomingAppt,
        recentEncounters,
        pendingRequests,
        activeCarePlan,
        recentReport,
        warnings);
  }

  private Map<String, Object> buildCareTeam(String practitionerId, List<String> warnings) {
    Map<String, Object> m = new LinkedHashMap<>();
    if (practitionerId != null) {
      try {
        Practitioner p = practitionerService.findPractitioner(practitionerId);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("id", p.getIdElement().getIdPart());
        if (p.hasName()) {
          doc.put(
              "name",
              "Dr. "
                  + p.getNameFirstRep().getGivenAsSingleString()
                  + " "
                  + p.getNameFirstRep().getFamily());
        }
        if (p.hasQualification() && !p.getQualification().isEmpty()) {
          doc.put("specialty", p.getQualificationFirstRep().getCode().getText());
        }
        m.put("primaryDoctor", doc);
      } catch (Exception e) {
        log.warn("Could not resolve practitioner {} for care team", practitionerId);
        warnings.add("care-team.practitioner: unavailable");
      }
    }
    try {
      Organization clinic =
          fhirClient.read().resource(Organization.class).withId(defaultOrgId).execute();
      Map<String, Object> orgMap = new LinkedHashMap<>();
      orgMap.put("id", clinic.getIdElement().getIdPart());
      orgMap.put("name", clinic.hasName() ? clinic.getName() : null);
      m.put("organization", orgMap);
    } catch (Exception e) {
      log.warn("Could not resolve org {}", defaultOrgId);
    }
    return m;
  }

  private List<Map<String, Object>> buildRecentEncounters(Bundle bundle) {
    List<Map<String, Object>> list = new ArrayList<>();
    if (bundle.getEntry() == null) return list;
    for (var entry : bundle.getEntry()) {
      Encounter enc = (Encounter) entry.getResource();
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", enc.getIdElement().getIdPart());
      if (enc.hasPeriod() && enc.getPeriod().hasStart()) {
        m.put(
            "date",
            enc.getPeriod()
                .getStart()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString());
      }
      m.put(
          "reason",
          enc.hasReasonCode() && !enc.getReasonCode().isEmpty()
              ? enc.getReasonCodeFirstRep().getText()
              : null);
      m.put("status", enc.getStatus() != null ? enc.getStatus().toCode() : null);
      list.add(m);
    }
    return list;
  }

  private String extractFirstAttendingId(Bundle bundle) {
    if (bundle.getEntry() == null || bundle.getEntry().isEmpty()) return null;
    return encounterMapper.attendingPractitionerId(
        (Encounter) bundle.getEntryFirstRep().getResource());
  }

  private Map<String, Object> buildUpcomingAppointment(
      Bundle bundle, Map<String, Object> careTeam) {
    if (bundle.getEntry() == null || bundle.getEntry().isEmpty()) return null;
    Appointment appt = (Appointment) bundle.getEntryFirstRep().getResource();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", appt.getIdElement().getIdPart());
    if (appt.hasStart()) {
      m.put(
          "start",
          appt.getStart().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString());
    }
    if (appt.hasDescription()) m.put("description", appt.getDescription());
    m.put("status", appt.getStatus() != null ? appt.getStatus().toCode() : null);
    if (careTeam.containsKey("primaryDoctor")) {
      m.put("provider", ((Map<?, ?>) careTeam.get("primaryDoctor")).get("name"));
    }
    return m;
  }

  private List<Map<String, Object>> buildPendingServiceRequests(
      Bundle bundle, Map<String, Object> careTeam) {
    List<Map<String, Object>> list = new ArrayList<>();
    if (bundle.getEntry() == null) return list;
    for (var entry : bundle.getEntry()) {
      ServiceRequest sr = (ServiceRequest) entry.getResource();
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", sr.getIdElement().getIdPart());
      m.put("code", sr.hasCode() ? sr.getCode().getText() : null);
      m.put(
          "category",
          sr.hasCategory() && !sr.getCategory().isEmpty()
              ? sr.getCategoryFirstRep().getText()
              : null);
      if (sr.hasAuthoredOn()) {
        m.put(
            "orderedOn",
            sr.getAuthoredOn().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString());
      }
      m.put("priority", sr.hasPriority() ? sr.getPriority().toCode() : null);
      m.put("status", sr.getStatus() != null ? sr.getStatus().toCode() : null);
      if (careTeam.containsKey("primaryDoctor")) {
        m.put("orderedBy", ((Map<?, ?>) careTeam.get("primaryDoctor")).get("name"));
      }
      list.add(m);
    }
    return list;
  }

  private Map<String, Object> buildRecentReport(DiagnosticReport rpt) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", rpt.getIdElement().getIdPart());
    m.put("title", rpt.hasCode() ? rpt.getCode().getText() : null);
    if (rpt.hasIssued()) {
      m.put(
          "issued",
          rpt.getIssued().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString());
    }
    m.put("conclusion", rpt.hasConclusion() ? rpt.getConclusion() : null);
    m.put("status", rpt.getStatus() != null ? rpt.getStatus().toCode() : null);
    if (rpt.hasBasedOn() && !rpt.getBasedOn().isEmpty()) {
      String srRef = rpt.getBasedOnFirstRep().getReference();
      if (srRef != null) m.put("serviceRequestId", srRef.replace("ServiceRequest/", ""));
    }
    return m;
  }

  private Map<String, Object> buildCarePlan(CarePlan cp, String patientId, List<String> warnings) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", cp.getIdElement().getIdPart());
    m.put("title", cp.hasTitle() ? cp.getTitle() : null);
    m.put("status", cp.getStatus() != null ? cp.getStatus().toCode() : null);

    List<Map<String, Object>> goalsList = new ArrayList<>();
    for (var goalRef : cp.getGoal()) {
      String goalId = goalRef.getReference().replace("Goal/", "");
      try {
        Goal goal = goalService.getGoal(goalId);
        goalsList.add(buildGoalWithProgress(goal, patientId));
      } catch (Exception e) {
        log.warn("Could not resolve goal {} — {}", goalId, e.getMessage());
        warnings.add("care-plan.goal." + goalId + ": unavailable");
      }
    }
    m.put("goals", goalsList);
    return m;
  }

  private Map<String, Object> buildGoalWithProgress(Goal goal, String patientId) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", goal.getIdElement().getIdPart());
    m.put("description", goal.hasDescription() ? goal.getDescription().getText() : null);
    m.put("status", goal.getLifecycleStatus() != null ? goal.getLifecycleStatus().toCode() : null);
    if (goal.hasTarget() && !goal.getTarget().isEmpty()) {
      Goal.GoalTargetComponent target = goal.getTargetFirstRep();
      if (target.hasDetail() && target.getDetail() instanceof Quantity q && q.hasValue()) {
        double targetValue = q.getValue().doubleValue();
        m.put("targetValue", targetValue);
        if (target.hasDue()) {
          m.put(
              "targetDate",
              target
                  .getDueDateType()
                  .getValue()
                  .toInstant()
                  .atZone(ZoneId.systemDefault())
                  .toLocalDate()
                  .toString());
        }
        GoalProgress progress = evaluateGoalProgress(goal, patientId, targetValue);
        m.put("progress", progress);
      }
    }
    return m;
  }

  private GoalProgress evaluateGoalProgress(Goal goal, String patientId, double targetValue) {
    if (!goal.hasTarget() || goal.getTarget().isEmpty()) {
      return new GoalProgress(null, targetValue, null, null, "No target defined", null);
    }
    Goal.GoalTargetComponent target = goal.getTargetFirstRep();
    if (!target.hasMeasure() || !target.getMeasure().hasCoding()) {
      return new GoalProgress(
          null, targetValue, null, null, "No matching measurements found", null);
    }
    String loincCode = target.getMeasure().getCodingFirstRep().getCode();
    if (loincCode == null) {
      return new GoalProgress(
          null, targetValue, null, null, "No matching measurements found", null);
    }
    try {
      Bundle bundle =
          fhirClient
              .search()
              .forResource(Observation.class)
              .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
              .and(new TokenClientParam("code").exactly().systemAndCode(LOINC_SYSTEM, loincCode))
              .sort()
              .descending("date")
              .count(1)
              .returnBundle(Bundle.class)
              .execute();
      if (bundle.getEntry() == null || bundle.getEntry().isEmpty()) {
        return new GoalProgress(
            null, targetValue, null, null, "No matching measurements found", null);
      }
      Observation obs = (Observation) bundle.getEntryFirstRep().getResource();
      if (!(obs.getValue() instanceof Quantity q) || !q.hasValue()) {
        return new GoalProgress(
            null, targetValue, null, null, "No matching measurements found", null);
      }
      double currentValue = q.getValue().doubleValue();
      boolean onTrack = currentValue <= targetValue;
      int percent =
          targetValue > 0
              ? (int) Math.min(100, Math.round((targetValue / Math.max(currentValue, 1)) * 100))
              : 0;

      Double baselineValue = null;
      try {
        Bundle earliestBundle =
            fhirClient
                .search()
                .forResource(Observation.class)
                .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
                .and(new TokenClientParam("code").exactly().systemAndCode(LOINC_SYSTEM, loincCode))
                .sort()
                .ascending("date")
                .count(1)
                .returnBundle(Bundle.class)
                .execute();
        if (earliestBundle.getEntry() != null && !earliestBundle.getEntry().isEmpty()) {
          Observation earliest = (Observation) earliestBundle.getEntryFirstRep().getResource();
          if (earliest.getValue() instanceof Quantity bq && bq.hasValue()) {
            baselineValue = bq.getValue().doubleValue();
          }
        }
      } catch (Exception e) {
        log.warn("Could not fetch baseline observation — {}", e.getMessage());
      }

      return new GoalProgress(currentValue, targetValue, onTrack, percent, null, baselineValue);
    } catch (Exception e) {
      log.warn("Could not evaluate goal progress — {}", e.getMessage());
      return new GoalProgress(null, targetValue, null, null, "Progress evaluation failed", null);
    }
  }
}
