package io.github.samarthshubham.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
  void devProfileProvidesOneStaffUserPerRoleAndTwoLinkedPatients() {
    runner
        .withPropertyValues("spring.profiles.active=dev")
        .run(
            context ->
                assertThat(context.getBean(AuthProperties.class).users())
                    .extracting(AuthProperties.User::role, AuthProperties.User::patientId)
                    .containsExactlyInAnyOrder(
                        tuple(Role.ADMIN, null),
                        tuple(Role.CLINICIAN, null),
                        tuple(Role.FRONT_DESK, null),
                        tuple(Role.PATIENT, "seed-tony-stark"),
                        tuple(Role.PATIENT, "seed-priya-nair")));
  }
}
