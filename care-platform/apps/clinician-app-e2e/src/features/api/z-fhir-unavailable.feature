@api @fhir-down
Feature: FHIR server unavailable

  Scenario: The API answers 503 when HAPI FHIR is down
    Given I am signed in to the API as "clinician"
    And the FHIR server is stopped
    When I send GET "/api/patients/any-patient-{runId}"
    Then the response status is 503
    And the error message contains "FHIR server unavailable"
