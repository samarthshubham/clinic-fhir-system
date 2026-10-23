@api
Feature: Snapshot alerts

  Background:
    Given I am signed in to the API as "clinician"
    And a test patient "patient"

  Scenario Outline: Blood pressure and SpO2 readings raise warning or critical alerts
    Given the test patient has these vitals:
      | daysAgo | <column> |
      | 0       | <value>  |
    When I send GET "/api/patients/{patient}/snapshot"
    Then the response status is 200
    And the response field "alerts" has 1 items
    And the response field "alerts.0.severity" is "<severity>"
    And the response field "alerts.0.message" contains "<text>"

    Examples:
      | column    | value | severity | text               |
      | systolic  | 190   | critical | Systolic BP 190    |
      | systolic  | 150   | warning  | Systolic BP 150    |
      | diastolic | 125   | critical | Diastolic BP 125   |
      | diastolic | 95    | warning  | Diastolic BP 95    |
      | spo2      | 85    | critical | SpO2 85.0%         |
      | spo2      | 93    | warning  | SpO2 93.0%         |

  Scenario Outline: Heart rate and temperature raise warnings
    Given the test patient has these vitals:
      | daysAgo | <column> |
      | 0       | <value>  |
    When I send GET "/api/patients/{patient}/snapshot"
    Then the response field "alerts" has 1 items
    And the response field "alerts.0.severity" is "warning"
    And the response field "alerts.0.message" contains "<text>"

    Examples:
      | column      | value | text         |
      | heartRate   | 120   | tachycardia  |
      | heartRate   | 45    | bradycardia  |
      | temperature | 38.9  | fever        |

  Scenario: Normal vitals raise no alerts
    Given the test patient has these vitals:
      | daysAgo | systolic | diastolic | spo2 | heartRate | temperature |
      | 0       | 118      | 76        | 98   | 72        | 36.7        |
    When I send GET "/api/patients/{patient}/snapshot"
    Then the response status is 200
    And the response field "alerts" has 0 items

  Scenario: Snapshot lists active conditions and current medications
    Given "clinician" creates "condition" with POST "/api/conditions":
      """
      {"patientId":"{patient}","code":"44054006","display":"Type 2 diabetes","clinicalStatus":"active","onsetDate":"{daysAgo:30}"}
      """
    And "clinician" creates "medication" with POST "/api/medications":
      """
      {"patientId":"{patient}","medicationName":"Metformin 500mg","status":"active","startDate":"{daysAgo:14}"}
      """
    When I send GET "/api/patients/{patient}/snapshot"
    Then the response field "activeConditions" has 1 items
    And the response field "currentMedications" has 1 items
