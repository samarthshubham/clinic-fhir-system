@ui
Feature: Recording vitals

  Background:
    Given the e2e login patients exist
    And the patient signs in to the patient app as "e2e-patient"

  Scenario: A patient records blood pressure and sees it in their history
    When the patient records blood pressure 128 over 82 in the patient app
    Then the patient sees the blood pressure "128/82" on the vitals page

  Scenario: The recorded reading is stored on the patient's own chart
    When the patient records blood pressure 133 over 85 in the patient app
    And I am signed in to the API as "e2e-patient"
    And I send GET "/api/vitals?patientId={patient}&type=systolic"
    Then the response has 1 items
    And the response field "items.0.value" is "133"

  Scenario: The review step shows the reading before it is saved
    When the patient enters blood pressure 165 over 100 without saving
    Then the review step shows blood pressure "165/100"
