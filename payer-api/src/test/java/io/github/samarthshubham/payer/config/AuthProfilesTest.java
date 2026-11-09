package io.github.samarthshubham.payer.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.payer.security.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AuthProfilesTest {

  @Configuration
  @EnableConfigurationProperties(AuthProperties.class)
  static class AuthPropertiesOnly {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withUserConfiguration(AuthPropertiesOnly.class);

  @Test
  void baseConfigurationShipsNoDemoAccountsOrClients() {
    runner.run(
        context -> {
          AuthProperties properties = context.getBean(AuthProperties.class);
          assertThat(properties.users()).isEmpty();
          assertThat(properties.clients()).isEmpty();
        });
  }

  @Test
  void devProfileProvidesOneStaffUserPerRoleAndRegistersClinicApi() {
    runner
        .withPropertyValues("spring.profiles.active=dev")
        .run(
            context -> {
              AuthProperties properties = context.getBean(AuthProperties.class);
              assertThat(properties.users())
                  .extracting(AuthProperties.User::role)
                  .containsExactlyInAnyOrder(Role.UM_REVIEWER, Role.PAYER_ADMIN);
              assertThat(properties.findClient("clinic-api").orElseThrow().scopes())
                  .containsExactly("system/Claim.c", "system/Questionnaire.r");
            });
  }

  @Test
  void clientWithANonSystemScopeIsRejected() {
    assertThatThrownBy(
            () ->
                new AuthProperties(
                    "secret",
                    "payer-api",
                    null,
                    null,
                    null,
                    null,
                    List.of(
                        new AuthProperties.Client("clinic-api", "hash", List.of("patient/*.r")))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("client 'clinic-api' has 'patient/*.r'");
  }
}
