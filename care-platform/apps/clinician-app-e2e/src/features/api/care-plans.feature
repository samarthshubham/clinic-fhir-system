@api
Feature: Care plans

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: Create a care plan linked to a condition and goal
    Given "clinician" creates "condition" with POST "/api/conditions":
      """
      {"patientId":"{patient}","code":"44054006","display":"Type 2 diabetes","clinicalStatus":"active","onsetDate":"{daysAgo:30}"}
      """
    And "clinician" creates "goal" with POST "/api/goals":
      """
      {"patientId":"{patient}","description":"HbA1c under 7","status":"active","targetMeasureCode":"4548-4","targetValue":7,"targetUnit":"%","targetDate":"{daysAhead:90}"}
      """
    When I send POST "/api/care-plans" with body:
      """
      {"patientId":"{patient}","title":"Diabetes management","status":"active","periodStart":"{daysAgo:1}","conditionIds":["{condition}"],"goalIds":["{goal}"]}
      """
    Then the response status is 201
    And the response field "title" is "Diabetes management"
    And the response field "conditionIds.0" is "{condition}"
    And the response field "goalIds.0" is "{goal}"

  Scenario: List care plans by patient
    Given "clinician" creates "plan" with POST "/api/care-plans":
      """
      {"patientId":"{patient}","title":"General wellness","status":"active","periodStart":"{daysAgo:1}"}
      """
    When I send GET "/api/care-plans?patientId={patient}"
    Then the response status is 200
    And the response has 1 items
    And some item in "items" has "title" equal to "General wellness"

  Scenario: Care plan without a title is rejected
    When I send POST "/api/care-plans" with body:
      """
      {"patientId":"{patient}","status":"active","periodStart":"{daysAgo:1}"}
      """
    Then the response status is 400
