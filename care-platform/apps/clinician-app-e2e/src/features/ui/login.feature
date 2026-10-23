@ui
Feature: Clinician sign-in

  Scenario: A clinician signs in and lands on the patient roster
    When I sign in to the clinician app as "clinician"
    Then I am on the patient roster

  Scenario: A wrong password shows an error
    Given I try to sign in to the clinician app with a wrong password
    Then I see the sign-in error "Invalid"
    And I am on the sign-in page

  Scenario: A signed-out visitor is sent to the sign-in page
    When I open the clinician app without signing in
    Then I am on the sign-in page
