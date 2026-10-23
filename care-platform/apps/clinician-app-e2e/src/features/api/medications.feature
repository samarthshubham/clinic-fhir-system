@api
Feature: Medications

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: Record a medication
    When I send POST "/api/medications" with body:
      """
      {"patientId":"{patient}","medicationName":"Metformin 500mg","status":"active","dosageText":"Twice daily","startDate":"{daysAgo:14}"}
      """
    Then the response status is 201
    And the response field "medicationName" is "Metformin 500mg"
    And the response field "dosageText" is "Twice daily"

  Scenario: List medications by patient
    Given "clinician" creates "med" with POST "/api/medications":
      """
      {"patientId":"{patient}","medicationName":"Lisinopril 10mg","status":"active","startDate":"{daysAgo:30}"}
      """
    When I send GET "/api/medications?patientId={patient}"
    Then the response status is 200
    And the response has 1 items
    And some item in "items" has "medicationName" equal to "Lisinopril 10mg"

  Scenario: Medication without a name is rejected
    When I send POST "/api/medications" with body:
      """
      {"patientId":"{patient}","status":"active","startDate":"{daysAgo:30}"}
      """
    Then the response status is 400
