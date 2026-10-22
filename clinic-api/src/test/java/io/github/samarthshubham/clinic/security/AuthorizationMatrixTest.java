package io.github.samarthshubham.clinic.security;

import static io.github.samarthshubham.clinic.security.Role.ADMIN;
import static io.github.samarthshubham.clinic.security.Role.CLINICIAN;
import static io.github.samarthshubham.clinic.security.Role.FRONT_DESK;
import static io.github.samarthshubham.clinic.security.Role.PATIENT;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.clinic.config.AuthProperties;
import io.github.samarthshubham.clinic.config.SecurityConfig;
import io.github.samarthshubham.clinic.service.AlertService;
import io.github.samarthshubham.clinic.service.AppointmentService;
import io.github.samarthshubham.clinic.service.AuthService;
import io.github.samarthshubham.clinic.service.CarePlanService;
import io.github.samarthshubham.clinic.service.ConditionService;
import io.github.samarthshubham.clinic.service.DashboardService;
import io.github.samarthshubham.clinic.service.DiagnosticReportService;
import io.github.samarthshubham.clinic.service.EncounterService;
import io.github.samarthshubham.clinic.service.GoalService;
import io.github.samarthshubham.clinic.service.HighBpAlertService;
import io.github.samarthshubham.clinic.service.MedicationService;
import io.github.samarthshubham.clinic.service.PatientService;
import io.github.samarthshubham.clinic.service.PractitionerService;
import io.github.samarthshubham.clinic.service.ServiceRequestService;
import io.github.samarthshubham.clinic.service.SnapshotService;
import io.github.samarthshubham.clinic.service.TimelineService;
import io.github.samarthshubham.clinic.service.TokenService;
import io.github.samarthshubham.clinic.service.TrendsService;
import io.github.samarthshubham.clinic.service.VitalsService;
import io.github.samarthshubham.clinic.support.TestAuth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.crypto.SecretKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest
@Import(SecurityConfig.class)
class AuthorizationMatrixTest {

  private static final Set<Role> EVERYONE = EnumSet.of(ADMIN, CLINICIAN, FRONT_DESK);
  private static final Set<Role> EVERYONE_AND_OWNER =
      EnumSet.of(ADMIN, CLINICIAN, FRONT_DESK, PATIENT);
  private static final Set<Role> CLINICAL_STAFF = EnumSet.of(ADMIN, CLINICIAN);
  private static final Set<Role> CLINICAL_STAFF_AND_OWNER = EnumSet.of(ADMIN, CLINICIAN, PATIENT);
  private static final Set<Role> ADMIN_ONLY = EnumSet.of(ADMIN);

  private static final List<Endpoint> ENDPOINTS =
      List.of(
          get("/api/patients", EVERYONE),
          get("/api/patients/p1", EVERYONE_AND_OWNER),
          post(
              "/api/patients",
              """
              {"firstName":"Priya","lastName":"Nair","dateOfBirth":"1985-03-14","gender":"female"}
              """,
              EVERYONE),
          get("/api/appointments?patientId=p1", EVERYONE_AND_OWNER),
          post(
              "/api/appointments",
              """
              {"patientId":"p1","start":"2024-06-01T09:00:00","end":"2024-06-01T09:30:00",
               "description":"Follow-up","status":"booked"}
              """,
              EVERYONE),
          get("/api/practitioners/pr1", EVERYONE),
          post(
              "/api/practitioners",
              """
              {"firstName":"Anil","lastName":"Rao","specialty":"Cardiology"}
              """,
              ADMIN_ONLY),
          get("/api/patients/p1/snapshot", CLINICAL_STAFF_AND_OWNER),
          get("/api/patients/p1/trends", CLINICAL_STAFF_AND_OWNER),
          get("/api/patients/p1/timeline", CLINICAL_STAFF_AND_OWNER),
          get("/api/dashboard/p1", CLINICAL_STAFF_AND_OWNER),
          get("/api/encounters/e1", CLINICAL_STAFF),
          get("/api/encounters?patientId=p1", CLINICAL_STAFF),
          post(
              "/api/encounters",
              """
              {"patientId":"p1","visitDate":"2024-05-01","reason":"Headache","status":"finished"}
              """,
              CLINICAL_STAFF),
          get("/api/vitals?patientId=p1", CLINICAL_STAFF_AND_OWNER),
          post(
              "/api/vitals",
              """
              {"patientId":"p1","encounterId":"e1","effectiveDate":"2024-05-01","systolicBp":142}
              """,
              CLINICAL_STAFF_AND_OWNER),
          get("/api/conditions/c1", CLINICAL_STAFF),
          get("/api/conditions?patientId=p1", CLINICAL_STAFF),
          post(
              "/api/conditions",
              """
              {"patientId":"p1","code":"I10","display":"Hypertension","clinicalStatus":"active",
               "onsetDate":"2024-01-15"}
              """,
              CLINICAL_STAFF),
          get("/api/medications?patientId=p1", CLINICAL_STAFF),
          get("/api/service-requests/sr1", CLINICAL_STAFF),
          get("/api/service-requests?patientId=p1", CLINICAL_STAFF),
          get("/api/diagnostic-reports?patientId=p1", CLINICAL_STAFF),
          get("/api/care-plans?patientId=p1", CLINICAL_STAFF),
          get("/api/goals?patientId=p1", CLINICAL_STAFF),
          get("/api/patients/p2", EVERYONE),
          get("/api/patients/p2/snapshot", CLINICAL_STAFF),
          get("/api/dashboard/p2", CLINICAL_STAFF),
          get("/api/appointments?patientId=p2", EVERYONE),
          post(
              "/api/vitals",
              """
              {"patientId":"p2","encounterId":"e1","effectiveDate":"2024-05-01","systolicBp":142}
              """,
              CLINICAL_STAFF),
          get("/api/alerts?status=active", CLINICAL_STAFF),
          post("/api/alerts/f1/acknowledge", null, CLINICAL_STAFF));

  @Autowired private MockMvcTester mvc;
  @Autowired private JwtEncoder jwtEncoder;
  @Autowired private SecretKey jwtSigningKey;
  @Autowired private AuthProperties authProperties;

  @MockitoBean private PatientService patientService;
  @MockitoBean private SnapshotService snapshotService;
  @MockitoBean private TrendsService trendsService;
  @MockitoBean private TimelineService timelineService;
  @MockitoBean private DashboardService dashboardService;
  @MockitoBean private AppointmentService appointmentService;
  @MockitoBean private PractitionerService practitionerService;
  @MockitoBean private EncounterService encounterService;
  @MockitoBean private VitalsService vitalsService;
  @MockitoBean private ConditionService conditionService;
  @MockitoBean private MedicationService medicationService;
  @MockitoBean private ServiceRequestService serviceRequestService;
  @MockitoBean private DiagnosticReportService diagnosticReportService;
  @MockitoBean private CarePlanService carePlanService;
  @MockitoBean private GoalService goalService;
  @MockitoBean private HighBpAlertService highBpAlertService;
  @MockitoBean private AlertService alertService;
  @MockitoBean private AuthService authService;

  static Stream<Arguments> endpoints() {
    return ENDPOINTS.stream().map(endpoint -> Arguments.of(endpoint));
  }

  static Stream<Arguments> endpointsByRole() {
    List<Arguments> cases = new ArrayList<>();
    for (Endpoint endpoint : ENDPOINTS) {
      for (Role role : Role.values()) {
        cases.add(Arguments.of(endpoint, role));
      }
    }
    return cases.stream();
  }

  @ParameterizedTest(name = "anonymous {0} -> 401")
  @MethodSource("endpoints")
  void requestsWithoutTokenAreUnauthorized(Endpoint endpoint) {
    assertThat(send(endpoint, null)).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @ParameterizedTest(name = "{1} {0}")
  @MethodSource("endpointsByRole")
  void eachRoleGetsExactlyItsAllowedEndpoints(Endpoint endpoint, Role role) {
    MvcTestResult result = send(endpoint, role);

    if (endpoint.allowed().contains(role)) {
      assertThat(result).hasStatus2xxSuccessful();
    } else {
      assertThat(result)
          .hasStatus(HttpStatus.FORBIDDEN)
          .bodyJson()
          .isStrictlyEqualTo(
              """
              {"status":403,"message":"Access denied"}
              """);
    }
  }

  private MvcTestResult send(Endpoint endpoint, Role role) {
    var request = mvc.method(endpoint.method()).uri(endpoint.uri());
    if (endpoint.body() != null) {
      request = request.contentType(MediaType.APPLICATION_JSON).content(endpoint.body());
    }
    if (role != null) {
      String token =
          new TokenService(jwtEncoder, jwtSigningKey, authProperties)
              .issueTokens(TestAuth.user(role))
              .accessToken();
      request = request.header("Authorization", "Bearer " + token);
    }
    return request.exchange();
  }

  private static Endpoint get(String uri, Set<Role> allowed) {
    return new Endpoint(HttpMethod.GET, uri, null, allowed);
  }

  private static Endpoint post(String uri, String body, Set<Role> allowed) {
    return new Endpoint(HttpMethod.POST, uri, body, allowed);
  }

  record Endpoint(HttpMethod method, String uri, String body, Set<Role> allowed) {
    @Override
    public String toString() {
      return method + " " + uri;
    }
  }
}
