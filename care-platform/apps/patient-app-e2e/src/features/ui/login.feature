@ui
Feature: Patient sign-in

  Background:
    Given the e2e login patients exist

  Scenario: A patient signs in and is greeted by first name
    When the patient signs in to the patient app as "e2e-patient"
    Then the patient app greets "Eve"
