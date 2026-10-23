@api
Feature: Practitioners

  Scenario: Create and fetch a practitioner
    Given I am signed in to the API as "admin"
    When I send POST "/api/practitioners" with body:
      """
      {"firstName":"Dana","lastName":"E2E-{runId}","specialty":"Cardiology","email":"dana-e2e@example.com"}
      """
    Then the response status is 201
    And the response field "specialty" is "Cardiology"
    When I remember the response field "id" as "practitioner"
    And I send GET "/api/practitioners/{practitioner}"
    Then the response status is 200
    And the response field "lastName" is "E2E-{runId}"
    And the response field "firstName" is "Dana"

  Scenario: Unknown practitioner returns 404
    Given I am signed in to the API as "admin"
    When I send GET "/api/practitioners/does-not-exist-{runId}"
    Then the response status is 404

  Scenario: Practitioner without a specialty is rejected
    Given I am signed in to the API as "admin"
    When I send POST "/api/practitioners" with body:
      """
      {"firstName":"Dana","lastName":"E2E-{runId}"}
      """
    Then the response status is 400
