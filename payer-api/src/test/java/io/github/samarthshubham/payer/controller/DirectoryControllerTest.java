package io.github.samarthshubham.payer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.samarthshubham.payer.config.SecurityConfig;
import io.github.samarthshubham.payer.security.Role;
import io.github.samarthshubham.payer.service.DirectoryService;
import io.github.samarthshubham.payer.support.WithRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(DirectoryController.class)
@Import(SecurityConfig.class)
class DirectoryControllerTest {

  @Autowired private MockMvcTester mvc;

  @MockitoBean private DirectoryService directory;

  @Test
  @WithRole(Role.UM_REVIEWER)
  void overlengthSpecialtyReturnsTheJsonErrorShape() {
    assertThat(mvc.get().uri("/api/directory").param("specialty", "x".repeat(101)))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"status":400,"message":"specialty size must be between 0 and 100"}
            """);
    verifyNoInteractions(directory);
  }
}
