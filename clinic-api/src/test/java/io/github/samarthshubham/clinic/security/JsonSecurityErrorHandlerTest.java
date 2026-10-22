package io.github.samarthshubham.clinic.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class JsonSecurityErrorHandlerTest {

  private final JsonSecurityErrorHandler handler = new JsonSecurityErrorHandler(new ObjectMapper());

  @Test
  void authenticationFailureWritesJson401() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.commence(
        new MockHttpServletRequest(), response, new BadCredentialsException("bad token"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(response.getContentAsString())
        .isEqualTo("{\"status\":401,\"message\":\"Authentication required\"}");
  }

  @Test
  void accessDeniedWritesJson403() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("nope"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString())
        .isEqualTo("{\"status\":403,\"message\":\"Access denied\"}");
  }
}
