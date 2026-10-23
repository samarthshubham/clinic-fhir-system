@api
Feature: Patient access to their own record

  Background:
    Given the e2e login patients exist
    And I am signed in to the API as "e2e-patient"

  Scenario: A patient reads their own record
    When I send GET "/api/patients/{patient}"
    Then the response status is 200
    And the response field "id" is "{patient}"
    When I send GET "/api/patients/{patient}/snapshot"
    Then the response status is 200
    When I send GET "/api/patients/{patient}/trends?type=bp"
    Then the response status is 200
    When I send GET "/api/patients/{patient}/timeline"
    Then the response status is 200
    When I send GET "/api/dashboard/{patient}"
    Then the response status is 200
    When I send GET "/api/appointments?patientId={patient}"
    Then the response status is 200

  Scenario: A patient records and lists their own vitals
    When I send POST "/api/vitals" with body:
      """
      {"patientId":"{patient}","effectiveDate":"{daysAgo:0}","systolicBp":132,"diastolicBp":84}
      """
    Then the response status is 201
    When I send GET "/api/vitals?patientId={patient}"
    Then the response status is 200
    And the response has 2 items
    When I send GET "/api/patients/{patient}/snapshot"
    Then the response field "latestVitals.systolicBp.value" is "132"

  Scenario: A patient cannot read another patient's record
    When I send GET "/api/patients/{other}"
    Then the response status is 403
    When I send GET "/api/patients/{other}/snapshot"
    Then the response status is 403
    When I send GET "/api/patients/{other}/timeline"
    Then the response status is 403
    When I send GET "/api/dashboard/{other}"
    Then the response status is 403
    When I send GET "/api/vitals?patientId={other}"
    Then the response status is 403

  Scenario: A patient cannot record vitals for another patient
    When I send POST "/api/vitals" with body:
      """
      {"patientId":"{other}","effectiveDate":"{daysAgo:0}","systolicBp":120}
      """
    Then the response status is 403

  Scenario: A patient cannot list or register patients
    When I send GET "/api/patients"
    Then the response status is 403
    When I send POST "/api/patients" with body:
      """
      {"firstName":"Test","lastName":"E2E-{runId}-self","dateOfBirth":"1990-01-02","gender":"male"}
      """
    Then the response status is 403

  Scenario: A patient cannot record clinical data
    When I send POST "/api/conditions" with body:
      """
      {"patientId":"{patient}","code":"44054006","display":"Type 2 diabetes","clinicalStatus":"active","onsetDate":"{daysAgo:30}"}
      """
    Then the response status is 403
    When I send GET "/api/alerts"
    Then the response status is 403
