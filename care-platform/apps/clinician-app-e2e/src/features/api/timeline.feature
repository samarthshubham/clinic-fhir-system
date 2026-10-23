@api
Feature: Timeline

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: Events are listed newest first
    Given the test patient has 4 encounters
    When I send GET "/api/patients/{patient}/timeline"
    Then the response status is 200
    And the response has 4 items
    And the "date" dates of "" are newest first

  Scenario: The default limit is 20 events
    Given the test patient has 25 encounters
    When I send GET "/api/patients/{patient}/timeline"
    Then the response has 20 items

  Scenario: The limit is capped at 50 events
    Given the test patient has 55 encounters
    When I send GET "/api/patients/{patient}/timeline?limit=100"
    Then the response has 50 items

  Scenario: The before cursor pages without overlap
    Given the test patient has 5 encounters
    When I send GET "/api/patients/{patient}/timeline?limit=2"
    Then the response has 2 items
    And the response field "0.date" is "{daysAgo:1}"
    And the response field "1.date" is "{daysAgo:2}"
    When I remember the "date" of the last item as "cursor"
    And I send GET "/api/patients/{patient}/timeline?limit=2&before={cursor}"
    Then the response has 2 items
    And the response field "0.date" is "{daysAgo:3}"
    And the response field "1.date" is "{daysAgo:4}"

  Scenario: The types filter limits the event types
    Given the test patient has 2 encounters
    And "clinician" creates "report" with POST "/api/diagnostic-reports":
      """
      {"patientId":"{patient}","title":"Chest X-ray","status":"final","issued":"{daysAgo:0}","conclusion":"Clear"}
      """
    When I send GET "/api/patients/{patient}/timeline?types=report"
    Then the response has 1 items
    And every item in "" has "type" equal to "report"
    When I send GET "/api/patients/{patient}/timeline?types=encounter"
    Then the response has 2 items
    And every item in "" has "type" equal to "encounter"

  Scenario: Abnormal vitals recorded at a visit are flagged
    Given "clinician" creates "visit" with POST "/api/encounters":
      """
      {"patientId":"{patient}","visitDate":"{daysAgo:1}","reason":"BP check","status":"finished"}
      """
    And "clinician" creates "reading" with POST "/api/vitals":
      """
      {"patientId":"{patient}","encounterId":"{visit}","effectiveDate":"{daysAgo:1}","systolicBp":165,"diastolicBp":70}
      """
    When I send GET "/api/patients/{patient}/timeline"
    Then the response field "0.groupedObservations" has 2 items
    And some item in "0.groupedObservations" has "flagged" equal to "true"
    And some item in "0.groupedObservations" has "flagged" equal to "false"
