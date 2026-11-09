@api
Feature: Coverages

  Background:
    Given I am signed in to the API as "frontdesk"
    And a test patient "patient"

  Scenario: Front desk records coverage and fetches it
    When I send POST "/api/coverages" with body:
      """
      {"patientId":"{patient}","payerId":"praxis-health-plan","payerName":"Praxis Health Plan","memberId":"PHP-9{runId}","planName":"Praxis Gold PPO","status":"active","periodStart":"{daysAgo:30}"}
      """
    Then the response status is 201
    And the response field "payerName" is "Praxis Health Plan"
    And the response field "relationship" is "self"
    And the response field "payerOrganizationId" is not empty
    When I remember the response field "id" as "coverage"
    And I send GET "/api/coverages/{coverage}"
    Then the response status is 200
    And the response field "memberId" is "PHP-9{runId}"

  Scenario: List a patient's coverages and see the active one on the snapshot
    Given "frontdesk" creates "old" with POST "/api/coverages":
      """
      {"patientId":"{patient}","payerId":"praxis-health-plan","payerName":"Praxis Health Plan","memberId":"PHP-8{runId}","status":"cancelled","periodStart":"{daysAgo:400}","periodEnd":"{daysAgo:31}"}
      """
    And "frontdesk" creates "current" with POST "/api/coverages":
      """
      {"patientId":"{patient}","payerId":"praxis-health-plan","payerName":"Praxis Health Plan","memberId":"PHP-7{runId}","status":"active","periodStart":"{daysAgo:30}"}
      """
    When I send GET "/api/coverages?patientId={patient}"
    Then the response status is 200
    And the response has 2 items
    Given I am signed in to the API as "clinician"
    When I send GET "/api/patients/{patient}/snapshot"
    Then the response field "coverage.id" is "{current}"
    And the response field "coverage.status" is "active"

  Scenario: The snapshot has no coverage without an active one
    Given I am signed in to the API as "clinician"
    When I send GET "/api/patients/{patient}/snapshot"
    Then the response status is 200
    And the response field "coverage" is null

  Scenario: Coverage without a member id is rejected
    When I send POST "/api/coverages" with body:
      """
      {"patientId":"{patient}","payerId":"praxis-health-plan","payerName":"Praxis Health Plan","status":"active","periodStart":"{daysAgo:0}"}
      """
    Then the response status is 400

  Scenario: Unknown coverage returns 404
    When I send GET "/api/coverages/does-not-exist-{runId}"
    Then the response status is 404
