@ui
Feature: Patient chart

  Scenario: A clinician reviews a patient with high blood pressure and low oxygen
    Given a test patient "chart"
    And the test patient has these vitals:
      | daysAgo | systolic | diastolic | spo2 | heartRate |
      | 3       | 150      | 92        | 92   | 78        |
      | 2       | 152      | 93        | 93   | 80        |
      | 1       | 185      | 95        | 93   | 82        |
    And "clinician" creates "encounter" with POST "/api/encounters":
      """
      {"patientId":"{patient}","visitDate":"{daysAgo:1}","reason":"Headache and dizziness","status":"finished"}
      """
    And I sign in to the clinician app as "clinician"
    When I open the test patient's chart
    Then the chart header shows the test patient
    And the chart shows a critical alert "Systolic BP 185"
    And the chart shows a warning alert "SpO2 93.0%"
    And the chart lists the active condition "Essential hypertension"
    And the chart lists the active condition "Hypoxemia"
    And the chart shows the latest blood pressure "185/95"
    And the chart timeline shows "Headache and dizziness"

  Scenario: Front desk cannot open a patient chart
    Given a test patient "frontdesk-chart"
    And I sign in to the clinician app as "frontdesk"
    When I open the test patient's chart
    Then I am redirected away from the patient chart
    And the navigation has no "Inbox" link
