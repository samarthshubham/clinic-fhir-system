package io.github.samarthshubham.clinic.dto;

import java.util.List;
import java.util.Map;

public record DashboardResponse(
    PatientSummary patient,
    Map<String, Object> careTeam,
    SnapshotResponse snapshot,
    Map<String, Object> upcomingAppointment,
    List<Map<String, Object>> recentEncounters,
    List<Map<String, Object>> pendingServiceRequests,
    Map<String, Object> activeCarePlan,
    Map<String, Object> recentDiagnosticReport,
    List<String> warnings) {}
