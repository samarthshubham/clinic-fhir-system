package io.github.samarthshubham.payer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.payer.config.SecurityConfig;
import io.github.samarthshubham.payer.dto.PageResponse;
import io.github.samarthshubham.payer.dto.PriorAuthQueueItem;
import io.github.samarthshubham.payer.security.Role;
import io.github.samarthshubham.payer.service.PriorAuthQueueService;
import io.github.samarthshubham.payer.support.WithRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(PriorAuthQueueController.class)
@Import(SecurityConfig.class)
class PriorAuthQueueControllerTest {

  @Autowired private MockMvcTester mvc;
  @MockitoBean private PriorAuthQueueService queue;

  @Test
  @WithRole(Role.UM_REVIEWER)
  void returnsPendingAuthorizationsAndSlaAsPlainJson() {
    when(queue.pended())
        .thenReturn(
            new PageResponse<>(
                List.of(
                    new PriorAuthQueueItem(
                        "1",
                        "PHP-PA-1",
                        "PHP-10006",
                        "Tony Stark",
                        "Cardiac stress test",
                        false,
                        Instant.parse("2026-11-26T10:00:00Z"),
                        -86_400,
                        true)),
                1));

    assertThat(mvc.get().uri("/api/prior-auths").param("status", "pended"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"items":[{"claimId":"1","authorizationReference":"PHP-PA-1","memberId":"PHP-10006",
            "memberName":"Tony Stark","service":"Cardiac stress test","expedited":false,
            "decisionDeadline":"2026-11-26T10:00:00Z","secondsRemaining":-86400,"breached":true}],"total":1}
            """);
  }

  @Test
  @WithRole(Role.PAYER_ADMIN)
  void reportsInvalidQueueStatus() {
    assertThat(mvc.get().uri("/api/prior-auths").param("status", "approved"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo(400);
  }
}
