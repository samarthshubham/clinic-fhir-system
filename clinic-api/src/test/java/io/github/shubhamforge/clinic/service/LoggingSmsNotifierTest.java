package io.github.shubhamforge.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.shubhamforge.clinic.dto.ClinicianAlert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LoggingSmsNotifierTest {

  @Test
  void logsMockSmsWithOnlyResourceReferences(CapturedOutput output) {
    new LoggingSmsNotifier()
        .send(new ClinicianAlert("Patient/p1", "Observation/o1", "Systolic BP above 180 mmHg"));

    assertThat(output)
        .contains(
            "[mock-sms] Clinician alert: Systolic BP above 180 mmHg for Patient/p1"
                + " (Observation/o1)");
  }
}
