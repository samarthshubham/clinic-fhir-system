Feature: Seed payer — prior authorizations

  Scenario: Tony Stark's backdated cardiac stress test is pended
    Given the seed has a payer-api system token
    When the seed submits the PAS fixture "request-cardiac-stress-test.json" 8 days ago
    Then the authorization response has a pended review action
