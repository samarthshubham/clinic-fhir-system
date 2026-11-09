package io.github.samarthshubham.payer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import io.github.samarthshubham.payer.config.SecurityConfig;
import io.github.samarthshubham.payer.dto.MemberCoverage;
import io.github.samarthshubham.payer.dto.MemberResponse;
import io.github.samarthshubham.payer.dto.MemberSummary;
import io.github.samarthshubham.payer.dto.PageResponse;
import io.github.samarthshubham.payer.dto.PlanSummary;
import io.github.samarthshubham.payer.exception.ResourceNotFoundException;
import io.github.samarthshubham.payer.security.Role;
import io.github.samarthshubham.payer.service.MemberService;
import io.github.samarthshubham.payer.support.WithRole;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class MemberControllerTest {

  private static final MemberSummary PRIYA =
      new MemberSummary(
          "m1",
          "PHP-10001",
          "Priya",
          "Nair",
          LocalDate.of(1985, 3, 14),
          "female",
          "Praxis Gold PPO",
          "active");

  @Autowired private MockMvcTester mvc;

  @MockitoBean private MemberService memberService;

  @Test
  @WithRole(Role.UM_REVIEWER)
  void searchPassesTheFiltersAndReturnsAPage() {
    when(memberService.searchMembers("Nair", "PHP-10001"))
        .thenReturn(new PageResponse<>(List.of(PRIYA), 1));

    assertThat(mvc.get().uri("/api/members").param("name", "Nair").param("memberId", "PHP-10001"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"items":[{"id":"m1","memberId":"PHP-10001","firstName":"Priya","lastName":"Nair",
                       "dateOfBirth":"1985-03-14","gender":"female","planName":"Praxis Gold PPO",
                       "coverageStatus":"active"}],
             "total":1}
            """);
  }

  @Test
  @WithRole(Role.PAYER_ADMIN)
  void searchWithoutFiltersPassesNulls() {
    when(memberService.searchMembers(null, null)).thenReturn(new PageResponse<>(List.of(), 0));

    assertThat(mvc.get().uri("/api/members"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo("{\"items\":[],\"total\":0}");
  }

  @Test
  @WithRole(Role.UM_REVIEWER)
  void detailReturnsCoveragePlanAndAnEmptyPriorAuthHistory() {
    when(memberService.getMember("m1"))
        .thenReturn(
            new MemberResponse(
                "m1",
                "PHP-10001",
                "Priya",
                "Nair",
                LocalDate.of(1985, 3, 14),
                "female",
                List.of(
                    new MemberCoverage(
                        "c1",
                        "active",
                        LocalDate.of(2025, 11, 9),
                        null,
                        "Praxis Health Plan",
                        "self",
                        new PlanSummary("plan-gold", "praxis-gold-ppo", "Praxis Gold PPO", "PPO"))),
                List.of()));

    assertThat(mvc.get().uri("/api/members/m1"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"id":"m1","memberId":"PHP-10001","firstName":"Priya","lastName":"Nair",
             "dateOfBirth":"1985-03-14","gender":"female",
             "coverages":[{"id":"c1","status":"active","periodStart":"2025-11-09","periodEnd":null,
                           "payerName":"Praxis Health Plan","relationship":"self",
                           "plan":{"id":"plan-gold","planId":"praxis-gold-ppo",
                                   "name":"Praxis Gold PPO","type":"PPO"}}],
             "priorAuths":[]}
            """);
  }

  @Test
  @WithRole(Role.UM_REVIEWER)
  void unknownMemberIsA404WithTheJsonErrorShape() {
    when(memberService.getMember("nope"))
        .thenThrow(new ResourceNotFoundException("Member", "nope"));

    assertThat(mvc.get().uri("/api/members/nope"))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .isStrictlyEqualTo("{\"status\":404,\"message\":\"Member with id 'nope' not found\"}");
  }

  @Test
  @WithRole(Role.UM_REVIEWER)
  void payerHapiDownIsA503() {
    when(memberService.searchMembers(null, null))
        .thenThrow(new FhirClientConnectionException("Connection refused"));

    assertThat(mvc.get().uri("/api/members"))
        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .bodyJson()
        .isStrictlyEqualTo("{\"status\":503,\"message\":\"Payer FHIR server unavailable\"}");
  }

  @Test
  @WithRole(Role.UM_REVIEWER)
  void payerHapiErrorIsA502WithoutItsDiagnostics() {
    when(memberService.getMember("m1"))
        .thenThrow(new InternalErrorException("HAPI-0000: details about Priya Nair"));

    assertThat(mvc.get().uri("/api/members/m1"))
        .hasStatus(HttpStatus.BAD_GATEWAY)
        .bodyJson()
        .isStrictlyEqualTo("{\"status\":502,\"message\":\"Payer FHIR server returned HTTP 500\"}");
  }

  @Test
  void anonymousGetsJson401() {
    assertThat(mvc.get().uri("/api/members"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo(401);
  }

  @Test
  @WithMockUser
  void userWithoutMemberReadGetsJson403() {
    assertThat(mvc.get().uri("/api/members/m1"))
        .hasStatus(HttpStatus.FORBIDDEN)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo(403);
  }
}
