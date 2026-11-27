@ui
Feature: Prior authorization review queue

  Scenario: A pended request appears in the queue
    Given a member with an overdue pended cardiac stress test authorization
    When the reviewer signs in to the payer app as "reviewer"
    Then the payer app shows the review queue
    And the queue shows the pended request with its deadline and breach
