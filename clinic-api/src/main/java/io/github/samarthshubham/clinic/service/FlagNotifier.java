package io.github.samarthshubham.clinic.service;

import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FlagNotifier implements Notifier {

  private final AlertService alertService;

  public FlagNotifier(AlertService alertService) {
    this.alertService = alertService;
  }

  @Override
  public void send(ClinicianAlert alert) {
    if (alertService.raise(alert)) {
      log.info("Clinician alert stored for {}", alert.observationReference());
    }
  }
}
