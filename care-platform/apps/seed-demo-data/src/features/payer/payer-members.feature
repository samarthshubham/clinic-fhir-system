Feature: Seed payer — members and their coverage on both sides

  Every demo profile is a Praxis Health Plan member. The provider Coverage goes
  through clinic-api; the member Patient and Coverage go to the payer HAPI as
  identifier-based conditional creates. Both Coverages carry the same member id
  (http://praxishealthplan.example/fhir/identifier/member-id), which is how the
  two sides find each other. Aisha Chen's coverage has ended (roadmap §5).

  Runs after the profile features (cucumber.json): each profile feature rebuilds
  its provider patient, which deletes the provider Coverage.

  Background: Praxis Health Plan is seeded
    Given the seed has a payer-api system token
    And payer organization "praxis-health-plan" named "Praxis Health Plan" is in the payer HAPI

  Scenario Outline: Enroll <patient> as member <memberId>
    Given the provider patient "<patient>"
    And the patient is covered by Praxis Health Plan:
      | memberId       | <memberId>       |
      | planName       | <planName>       |
      | status         | <status>         |
      | startedDaysAgo | <startedDaysAgo> |
      | endedDaysAgo   | <endedDaysAgo>   |
    Then Praxis Health Plan has the patient as a member with the same coverage

    Examples:
      | patient             | memberId  | planName        | status    | startedDaysAgo | endedDaysAgo |
      | seed-priya-nair     | PHP-10001 | Praxis Gold PPO | active    | 365            |              |
      | seed-ramon-castillo | PHP-10002 | Praxis Gold PPO | active    | 365            |              |
      | seed-gerald-horton  | PHP-10003 | Praxis Gold PPO | active    | 365            |              |
      | seed-marcus-webb    | PHP-10004 | Praxis Gold PPO | active    | 365            |              |
      | seed-sandra-okafor  | PHP-10005 | Praxis Gold PPO | active    | 365            |              |
      | seed-tony-stark     | PHP-10006 | Praxis Gold PPO | active    | 365            |              |
      | seed-robert-osei    | PHP-10007 | Praxis Gold PPO | active    | 365            |              |
      | seed-aisha-chen     | PHP-10008 | Praxis Gold PPO | cancelled | 400            | 30           |
