@ui
Feature: Reviewer sign-in

  Scenario: A reviewer logs in and sees an empty queue
    When the reviewer signs in to the payer app as "reviewer"
    Then the payer app shows the review queue
    And the review queue is empty
