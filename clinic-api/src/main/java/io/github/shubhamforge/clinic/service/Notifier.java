package io.github.shubhamforge.clinic.service;

import io.github.shubhamforge.clinic.dto.ClinicianAlert;

public interface Notifier {

  void send(ClinicianAlert alert);
}
