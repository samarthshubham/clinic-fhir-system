@api
Feature: Goals

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: Create a goal
    When I send POST "/api/goals" with body:
      """
      {"patientId":"{patient}","description":"Bring systolic BP under 130","status":"active","targetMeasureCode":"8480-6","targetMeasureDisplay":"Systolic BP","targetValue":130,"targetUnit":"mm[Hg]","targetDate":"{daysAhead:90}"}
      """
    Then the response status is 201
    And the response field "description" is "Bring systolic BP under 130"
    And the response field "targetMeasureCode" is "8480-6"

  Scenario: List goals by patient
    Given "clinician" creates "goal" with POST "/api/goals":
      """
      {"patientId":"{patient}","description":"Lose 5 kg","status":"active","targetMeasureCode":"29463-7","targetValue":65,"targetUnit":"kg","targetDate":"{daysAhead:60}"}
      """
    When I send GET "/api/goals?patientId={patient}"
    Then the response status is 200
    And the response has 1 items

  Scenario: Goal without a description is rejected
    When I send POST "/api/goals" with body:
      """
      {"patientId":"{patient}","status":"active"}
      """
    Then the response status is 400
