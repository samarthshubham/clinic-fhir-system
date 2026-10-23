@api
Feature: Auto-detected conditions

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario: Three consecutive high systolic readings create hypertension
    Given the test patient has these vitals:
      | daysAgo | systolic | diastolic |
      | 3       | 150      | 85        |
      | 2       | 152      | 86        |
      | 1       | 155      | 88        |
    When I send GET "/api/conditions?patientId={patient}"
    Then the response has 1 items
    And some item in "items" has "code" equal to "59621000"
    And some item in "items" has "clinicalStatus" equal to "active"

  Scenario: Two high readings do not create a condition
    Given the test patient has these vitals:
      | daysAgo | systolic | diastolic |
      | 2       | 150      | 85        |
      | 1       | 152      | 86        |
    When I send GET "/api/conditions?patientId={patient}"
    Then the response has 0 items

  Scenario: A normal reading among the last three prevents hypertension
    Given the test patient has these vitals:
      | daysAgo | systolic | diastolic |
      | 3       | 150      | 85        |
      | 2       | 120      | 80        |
      | 1       | 152      | 86        |
    When I send GET "/api/conditions?patientId={patient}"
    Then the response has 0 items

  Scenario: A fourth high reading does not duplicate the condition
    Given the test patient has these vitals:
      | daysAgo | systolic | diastolic |
      | 4       | 150      | 85        |
      | 3       | 152      | 86        |
      | 2       | 155      | 88        |
      | 1       | 158      | 90        |
    When I send GET "/api/conditions?patientId={patient}"
    Then the response has 1 items

  Scenario: Two consecutive low SpO2 readings create hypoxemia
    Given the test patient has these vitals:
      | daysAgo | spo2 |
      | 2       | 92   |
      | 1       | 93   |
    When I send GET "/api/conditions?patientId={patient}"
    Then the response has 1 items
    And some item in "items" has "code" equal to "86290005"
    And some item in "items" has "display" equal to "Hypoxemia"

  Scenario: One low SpO2 reading does not create a condition
    Given the test patient has these vitals:
      | daysAgo | spo2 |
      | 2       | 98   |
      | 1       | 92   |
    When I send GET "/api/conditions?patientId={patient}"
    Then the response has 0 items

  Scenario: A third low SpO2 reading does not duplicate the condition
    Given the test patient has these vitals:
      | daysAgo | spo2 |
      | 3       | 92   |
      | 2       | 93   |
      | 1       | 91   |
    When I send GET "/api/conditions?patientId={patient}"
    Then the response has 1 items
