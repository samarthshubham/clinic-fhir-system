@api
Feature: Encounters

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: Record an encounter for a patient
    When I send POST "/api/encounters" with body:
      """
      {"patientId":"{patient}","visitDate":"{daysAgo:3}","reason":"Annual check-up","status":"finished","note":"All good"}
      """
    Then the response status is 201
    And the response field "patientId" is "{patient}"
    And the response field "reason" is "Annual check-up"
    And the response field "note" is "All good"

  Scenario: Get an encounter by id
    Given "clinician" creates "encounter" with POST "/api/encounters":
      """
      {"patientId":"{patient}","visitDate":"{daysAgo:1}","reason":"Follow-up","status":"finished"}
      """
    When I send GET "/api/encounters/{encounter}"
    Then the response status is 200
    And the response field "id" is "{encounter}"
    And the response field "status" is "finished"

  Scenario: List encounters by patient
    Given "clinician" creates "first" with POST "/api/encounters":
      """
      {"patientId":"{patient}","visitDate":"{daysAgo:10}","reason":"Cough","status":"finished"}
      """
    And "clinician" creates "second" with POST "/api/encounters":
      """
      {"patientId":"{patient}","visitDate":"{daysAgo:2}","reason":"Fever","status":"finished"}
      """
    When I send GET "/api/encounters?patientId={patient}"
    Then the response status is 200
    And the response has 2 items
    And every item in "items" has "patientId" equal to "{patient}"

  Scenario: Unknown encounter returns 404
    When I send GET "/api/encounters/does-not-exist-{runId}"
    Then the response status is 404

  Scenario: Encounter without a visit date is rejected
    When I send POST "/api/encounters" with body:
      """
      {"patientId":"{patient}","reason":"No date","status":"finished"}
      """
    Then the response status is 400
