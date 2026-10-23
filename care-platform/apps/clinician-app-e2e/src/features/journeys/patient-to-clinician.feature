@journey @ui
Feature: A patient's readings reach their clinician

  Background:
    Given the e2e login patients exist

  Scenario: The patient records a blood pressure and the clinician sees it on the chart
    When the patient signs in to the patient app as "e2e-patient"
    And the patient records blood pressure 150 over 95 in the patient app
    And I sign in to the clinician app as "clinician"
    And I open the test patient's chart
    Then the chart shows the latest blood pressure "150/95"
    And the chart shows a warning alert "Systolic BP 150"

  Scenario: Three high readings in a row give the patient a hypertension diagnosis
    Given the test patient has these vitals:
      | daysAgo | systolic | diastolic |
      | 3       | 148      | 92        |
      | 2       | 151      | 94        |
      | 1       | 155      | 96        |
    When I sign in to the clinician app as "clinician"
    And I open the test patient's chart
    Then the chart lists the active condition "Essential hypertension"
    And the chart shows the latest blood pressure "155/96"

  Scenario: A critical reading reaches the clinician inbox and can be acknowledged
    When the patient signs in to the patient app as "e2e-patient"
    And the patient records blood pressure 190 over 115 in the patient app
    And I sign in to the clinician app as "clinician"
    And I open the clinician inbox
    Then the inbox shows a high BP alert for the test patient
    When I acknowledge the inbox alert for the test patient
    Then the inbox no longer shows the test patient

  Scenario: A reading recorded by the clinician appears in the patient's app
    Given the test patient has these vitals:
      | daysAgo | systolic | diastolic | weight |
      | 0       | 126      | 82        | 71     |
    When the patient signs in to the patient app as "e2e-patient"
    Then the patient app greets "Eve"
    And the patient sees the blood pressure "126/82" on the vitals page
