@ui
Feature: Reviewer sign-in

  Scenario: A reviewer logs in and opens the review queue
    When the reviewer signs in to the payer app as "reviewer"
    Then the payer app shows the review queue
