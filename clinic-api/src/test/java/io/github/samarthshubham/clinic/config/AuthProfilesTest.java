package io.github.samarthshubham.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.clinic.security.Role;
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
  void baseConfigurationShipsNoDemoUsers() {
    runner.run(context -> assertThat(context.getBean(AuthProperties.class).users()).isEmpty());
  }

  @Test
  void devProfileProvidesOneDemoUserPerRole() {
    runner
        .withPropertyValues("spring.profiles.active=dev")
        .run(
            context ->
                assertThat(context.getBean(AuthProperties.class).users())
                    .extracting(AuthProperties.User::role)
                    .containsExactlyInAnyOrder(Role.ADMIN, Role.CLINICIAN, Role.FRONT_DESK));
  }
}
