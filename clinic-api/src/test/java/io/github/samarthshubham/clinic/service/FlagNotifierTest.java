package io.github.samarthshubham.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class FlagNotifierTest {

  private static final ClinicianAlert ALERT =
      new ClinicianAlert("Patient/p1", "Observation/obs-9", "Systolic BP above 180 mmHg");

  private final AlertService alertService = mock(AlertService.class);
  private final FlagNotifier notifier = new FlagNotifier(alertService);

  @Test
  void storesTheAlertAndLogsOnlyTheReference(CapturedOutput output) {
    when(alertService.raise(ALERT)).thenReturn(true);

    notifier.send(ALERT);

    verify(alertService).raise(ALERT);
    assertThat(output).contains("Clinician alert stored for Observation/obs-9");
  }

  @Test
  void staysQuietForAnAlertThatIsAlreadyStored(CapturedOutput output) {
    when(alertService.raise(ALERT)).thenReturn(false);

    notifier.send(ALERT);

    assertThat(output).doesNotContain("Clinician alert stored");
  }
}
