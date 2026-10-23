@api
Feature: Diagnostic reports

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: A report completes the order it is based on
    Given "clinician" creates "order" with POST "/api/service-requests":
      """
      {"patientId":"{patient}","code":"Complete blood count","category":"laboratory","status":"active","priority":"routine","authoredOn":"{daysAgo:2}"}
      """
    When I send POST "/api/diagnostic-reports" with body:
      """
      {"patientId":"{patient}","serviceRequestId":"{order}","title":"CBC result","status":"final","issued":"{daysAgo:0}","conclusion":"Within normal limits"}
      """
    Then the response status is 201
    And the response field "title" is "CBC result"
    And the response field "serviceRequestId" is "{order}"
    When I send GET "/api/service-requests/{order}"
    Then the response field "status" is "completed"

  Scenario: List reports by patient
    Given "clinician" creates "report" with POST "/api/diagnostic-reports":
      """
      {"patientId":"{patient}","title":"Chest X-ray","status":"final","issued":"{daysAgo:1}","conclusion":"Clear"}
      """
    When I send GET "/api/diagnostic-reports?patientId={patient}"
    Then the response status is 200
    And the response has 1 items
    And some item in "items" has "conclusion" equal to "Clear"

  Scenario: Report without a title is rejected
    When I send POST "/api/diagnostic-reports" with body:
      """
      {"patientId":"{patient}","status":"final","issued":"{daysAgo:0}"}
      """
    Then the response status is 400
