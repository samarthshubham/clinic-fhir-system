@api
Feature: Clinician alerts

  Scenario: A clinician can list active alerts
    Given I am signed in to the API as "clinician"
    When I send GET "/api/alerts?status=active"
    Then the response status is 200
    And the response field "items" has at least 0 items

  Scenario: Acknowledging an unknown alert returns 404
    Given I am signed in to the API as "clinician"
    When I send POST "/api/alerts/does-not-exist-{runId}/acknowledge"
    Then the response status is 404

  Scenario: The alert webhook rejects a call without the shared secret
    When I send POST "/api/alerts/bp-subscription"
    Then the response status is 401
