package io.github.samarthshubham.clinic.service;

import io.github.samarthshubham.clinic.dto.ClinicianAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingSmsNotifier implements Notifier {

  @Override
  public void send(ClinicianAlert alert) {
    log.info(
        "[mock-sms] Clinician alert: {} for {} ({})",
        alert.reason(),
        alert.patientReference(),
        alert.observationReference());
  }
}
