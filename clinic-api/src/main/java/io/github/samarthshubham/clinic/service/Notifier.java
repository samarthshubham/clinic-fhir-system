package io.github.samarthshubham.clinic.service;

import io.github.samarthshubham.clinic.dto.ClinicianAlert;

public interface Notifier {

  void send(ClinicianAlert alert);
}
