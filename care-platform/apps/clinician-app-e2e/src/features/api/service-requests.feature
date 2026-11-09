@api
Feature: Service requests

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: Place an order and fetch it
    When I send POST "/api/service-requests" with body:
      """
      {"patientId":"{patient}","display":"Complete blood count","category":"laboratory","status":"active","priority":"routine","authoredOn":"{daysAgo:0}"}
      """
    Then the response status is 201
    And the response field "display" is "Complete blood count"
    When I remember the response field "id" as "order"
    And I send GET "/api/service-requests/{order}"
    Then the response status is 200
    And the response field "status" is "active"

  Scenario: Place a coded order
    When I send POST "/api/service-requests" with body:
      """
      {"patientId":"{patient}","display":"MRI lumbar spine","codeSystem":"http://www.ama-assn.org/go/cpt","code":"72148","category":"imaging","status":"active","priority":"routine","authoredOn":"{daysAgo:0}"}
      """
    Then the response status is 201
    And the response field "codeSystem" is "http://www.ama-assn.org/go/cpt"
    And the response field "code" is "72148"
    And the response field "display" is "MRI lumbar spine"

  Scenario: Order with a code but no code system is rejected
    When I send POST "/api/service-requests" with body:
      """
      {"patientId":"{patient}","display":"MRI lumbar spine","code":"72148","category":"imaging","status":"active","priority":"routine","authoredOn":"{daysAgo:0}"}
      """
    Then the response status is 400
    And the error message contains "codeSystem and code must be given together"

  Scenario: Filter orders by status
    Given "clinician" creates "open" with POST "/api/service-requests":
      """
      {"patientId":"{patient}","display":"Chest X-ray","category":"imaging","status":"active","priority":"routine","authoredOn":"{daysAgo:1}"}
      """
    And "clinician" creates "done" with POST "/api/service-requests":
      """
      {"patientId":"{patient}","display":"Lipid panel","category":"laboratory","status":"completed","priority":"routine","authoredOn":"{daysAgo:5}"}
      """
    When I send GET "/api/service-requests?patientId={patient}"
    Then the response has 2 items
    When I send GET "/api/service-requests?patientId={patient}&status=active"
    Then the response has 1 items
    And every item in "items" has "status" equal to "active"

  Scenario: Unknown order returns 404
    When I send GET "/api/service-requests/does-not-exist-{runId}"
    Then the response status is 404

  Scenario: Order without a priority is rejected
    When I send POST "/api/service-requests" with body:
      """
      {"patientId":"{patient}","display":"CBC","category":"laboratory","status":"active","authoredOn":"{daysAgo:0}"}
      """
    Then the response status is 400
