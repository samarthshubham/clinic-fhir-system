@api
Feature: Patients

  Background:
    Given I am signed in to the API as "clinician"

  Scenario: Register a patient
    When I send POST "/api/patients" with body:
      """
      {"firstName":"Test","lastName":"E2E-{runId}-register","dateOfBirth":"1990-01-02","gender":"female","phone":"555-0100","email":"e2e@example.com"}
      """
    And I track the response id as the test patient
    Then the response status is 201
    And the response field "id" is not empty
    And the response field "firstName" is "Test"
    And the response field "dateOfBirth" is "1990-01-02"
    And the response field "gender" is "female"
    And the response field "phone" is "555-0100"

  Scenario: Get a patient by id
    Given a test patient "alice"
    When I send GET "/api/patients/{alice}"
    Then the response status is 200
    And the response field "id" is "{alice}"
    And the response field "firstName" is "Test"

  Scenario: Unknown patient id returns 404
    When I send GET "/api/patients/does-not-exist-{runId}"
    Then the response status is 404
    And the response field "status" is "404"

  Scenario: Search patients by name
    Given a test patient "searchable"
    When I send GET "/api/patients?name=E2E-searchable"
    Then the response status is 200
    And some item in "items" has "id" equal to "{searchable}"

  Scenario: Search patients by date of birth
    Given a test patient "born-same-day"
    When I send GET "/api/patients?dob=1980-05-15"
    Then the response status is 200
    And some item in "items" has "id" equal to "{born-same-day}"
    And every item in "items" has "dateOfBirth" equal to "1980-05-15"

  Scenario: Registering without a last name is rejected
    When I send POST "/api/patients" with body:
      """
      {"firstName":"Test","dateOfBirth":"1990-01-02","gender":"female"}
      """
    Then the response status is 400

  Scenario: Registering without a date of birth is rejected
    When I send POST "/api/patients" with body:
      """
      {"firstName":"Test","lastName":"E2E-{runId}-nodob","gender":"female"}
      """
    Then the response status is 400
