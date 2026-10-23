@api
Feature: Authentication and role restrictions

  Scenario: Requests without a token are rejected
    Given I am not signed in to the API
    When I send GET "/api/patients"
    Then the response status is 401
    When I send POST "/api/vitals" with body:
      """
      {"patientId":"p1","effectiveDate":"{daysAgo:0}","systolicBp":120}
      """
    Then the response status is 401

  Scenario: A tampered token is rejected
    Given I am signed in to the API with an invalid token
    When I send GET "/api/patients"
    Then the response status is 401

  Scenario: Login with a wrong password fails
    Given I am not signed in to the API
    When I send POST "/api/auth/login" with body:
      """
      {"username":"clinician","password":"wrong-password"}
      """
    Then the response status is 401

  Scenario: A refresh token gets a new access token but cannot be used as one
    Given I am not signed in to the API
    When I send POST "/api/auth/login" with body:
      """
      {"username":"clinician","password":"Clinician#2026"}
      """
    Then the response status is 200
    And the response field "user.roles.0" is "CLINICIAN"
    When I remember the response field "refreshToken" as "refresh"
    And I send POST "/api/auth/refresh" with body:
      """
      {"refreshToken":"{refresh}"}
      """
    Then the response status is 200
    And the response field "accessToken" is not empty
    When I use the remembered "refresh" as my bearer token
    And I send GET "/api/patients"
    Then the response status is 401

  Scenario: Front desk can register patients, search them and book appointments
    Given I am signed in to the API as "frontdesk"
    When I send POST "/api/patients" with body:
      """
      {"firstName":"Test","lastName":"E2E-{runId}-frontdesk","dateOfBirth":"1990-01-02","gender":"male"}
      """
    Then the response status is 201
    When I remember the response field "id" as "patient"
    And I send GET "/api/patients?name=E2E-{runId}-frontdesk"
    Then the response status is 200
    When I send POST "/api/appointments" with body:
      """
      {"patientId":"{patient}","start":"{daysAhead:2}T09:00:00","end":"{daysAhead:2}T09:30:00","status":"booked"}
      """
    Then the response status is 201

  Scenario: Front desk cannot touch clinical data
    Given a test patient "patient"
    And I am signed in to the API as "frontdesk"
    When I send POST "/api/vitals" with body:
      """
      {"patientId":"{patient}","effectiveDate":"{daysAgo:0}","systolicBp":120}
      """
    Then the response status is 403
    When I send POST "/api/conditions" with body:
      """
      {"patientId":"{patient}","code":"44054006","display":"Type 2 diabetes","clinicalStatus":"active","onsetDate":"{daysAgo:30}"}
      """
    Then the response status is 403
    When I send GET "/api/vitals?patientId={patient}"
    Then the response status is 403
    When I send GET "/api/patients/{patient}/snapshot"
    Then the response status is 403
    When I send GET "/api/alerts"
    Then the response status is 403

  Scenario: Only an admin can register practitioners
    Given I am signed in to the API as "clinician"
    When I send POST "/api/practitioners" with body:
      """
      {"firstName":"Dana","lastName":"E2E-{runId}-denied","specialty":"Cardiology"}
      """
    Then the response status is 403
    Given I am signed in to the API as "admin"
    When I send POST "/api/practitioners" with body:
      """
      {"firstName":"Dana","lastName":"E2E-{runId}-allowed","specialty":"Cardiology"}
      """
    Then the response status is 201

  Scenario: A clinician has full clinical access
    Given a test patient "patient"
    And I am signed in to the API as "clinician"
    When I send POST "/api/vitals" with body:
      """
      {"patientId":"{patient}","effectiveDate":"{daysAgo:0}","systolicBp":120}
      """
    Then the response status is 201
    When I send GET "/api/patients/{patient}/snapshot"
    Then the response status is 200
