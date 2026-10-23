@api
Feature: Conditions

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: Record a diagnosis and fetch it
    When I send POST "/api/conditions" with body:
      """
      {"patientId":"{patient}","code":"44054006","display":"Type 2 diabetes","clinicalStatus":"active","onsetDate":"{daysAgo:30}"}
      """
    Then the response status is 201
    And the response field "display" is "Type 2 diabetes"
    And the response field "clinicalStatus" is "active"
    When I remember the response field "id" as "condition"
    And I send GET "/api/conditions/{condition}"
    Then the response status is 200
    And the response field "code" is "44054006"
    And the response field "patientId" is "{patient}"

  Scenario: List conditions by patient
    Given "clinician" creates "first" with POST "/api/conditions":
      """
      {"patientId":"{patient}","code":"44054006","display":"Type 2 diabetes","clinicalStatus":"active","onsetDate":"{daysAgo:30}"}
      """
    And "clinician" creates "second" with POST "/api/conditions":
      """
      {"patientId":"{patient}","code":"195967001","display":"Asthma","clinicalStatus":"active","onsetDate":"{daysAgo:90}"}
      """
    When I send GET "/api/conditions?patientId={patient}"
    Then the response status is 200
    And the response has 2 items

  Scenario: Unknown condition returns 404
    When I send GET "/api/conditions/does-not-exist-{runId}"
    Then the response status is 404

  Scenario: Condition without a code is rejected
    When I send POST "/api/conditions" with body:
      """
      {"patientId":"{patient}","display":"Asthma","clinicalStatus":"active","onsetDate":"{daysAgo:30}"}
      """
    Then the response status is 400
