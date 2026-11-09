Feature: Seed payer — Praxis Health Plan and its insurance plans

  The fictional payer and the two plans it sells, in the payer HAPI (:8081).
  payer-api has no write endpoints yet, so these go straight to the payer HAPI
  as identifier-based conditional creates: a rerun finds them and changes nothing.

  Background: payer-api knows the seed client
    Given the seed has a payer-api system token

  Scenario: Seed Praxis Health Plan
    Given payer organization "praxis-health-plan" named "Praxis Health Plan" is in the payer HAPI
    And the payer offers these insurance plans:
      | planId     | name              | planType | startedDaysAgo |
      | gold-ppo   | Praxis Gold PPO   | PPO      | 730            |
      | silver-hmo | Praxis Silver HMO | HMO      | 730            |
