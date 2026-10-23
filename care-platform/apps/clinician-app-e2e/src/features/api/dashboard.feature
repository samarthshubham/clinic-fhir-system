@api
Feature: Dashboard

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: The dashboard returns every section for a patient with a full record
    Given "clinician" creates "encounter" with POST "/api/encounters":
      """
      {"patientId":"{patient}","visitDate":"{daysAgo:2}","reason":"Check-up","status":"finished"}
      """
    And the test patient has these vitals:
      | daysAgo | systolic | diastolic |
      | 1       | 150      | 92        |
    And "clinician" creates "condition" with POST "/api/conditions":
      """
      {"patientId":"{patient}","code":"44054006","display":"Type 2 diabetes","clinicalStatus":"active","onsetDate":"{daysAgo:30}"}
      """
    And "clinician" creates "appointment" with POST "/api/appointments":
      """
      {"patientId":"{patient}","start":"{daysAhead:5}T09:00:00","end":"{daysAhead:5}T09:30:00","description":"Follow-up","status":"booked"}
      """
    And "clinician" creates "order" with POST "/api/service-requests":
      """
      {"patientId":"{patient}","code":"Lipid panel","category":"laboratory","status":"active","priority":"routine","authoredOn":"{daysAgo:1}"}
      """
    And "clinician" creates "plan" with POST "/api/care-plans":
      """
      {"patientId":"{patient}","title":"Diabetes management","status":"active","periodStart":"{daysAgo:1}"}
      """
    And "clinician" creates "report" with POST "/api/diagnostic-reports":
      """
      {"patientId":"{patient}","title":"Chest X-ray","status":"final","issued":"{daysAgo:1}","conclusion":"Clear"}
      """
    When I send GET "/api/dashboard/{patient}"
    Then the response status is 200
    And the response field "patient.id" is "{patient}"
    And the response field "snapshot.activeConditions" has 1 items
    And the response field "snapshot.latestVitals.systolicBp.value" is "150"
    And the response field "upcomingAppointment.description" is "Follow-up"
    And the response field "recentEncounters" has 1 items
    And the response field "pendingServiceRequests" has 1 items
    And the response field "activeCarePlan.title" is "Diabetes management"
    And the response field "recentDiagnosticReport.title" is "Chest X-ray"
    And the response field "warnings" has 0 items

  Scenario: The dashboard for an empty record still succeeds
    When I send GET "/api/dashboard/{patient}"
    Then the response status is 200
    And the response field "patient.id" is "{patient}"
    And the response field "recentEncounters" has 0 items
    And the response field "warnings" has 0 items

  Scenario: The dashboard for an unknown patient returns 404
    When I send GET "/api/dashboard/does-not-exist-{runId}"
    Then the response status is 404
