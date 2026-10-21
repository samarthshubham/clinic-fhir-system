package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.AppointmentRequest;
import io.github.samarthshubham.clinic.service.AppointmentService;
import jakarta.validation.Valid;
import org.hl7.fhir.r4.model.Appointment;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

  private final AppointmentService appointmentService;

  public AppointmentController(AppointmentService appointmentService) {
    this.appointmentService = appointmentService;
  }

  @PostMapping
  public ResponseEntity<Appointment> createAppointment(
      @Valid @RequestBody AppointmentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(appointmentService.createAppointment(request));
  }

  @GetMapping
  public ResponseEntity<Bundle> getAppointmentsForPatient(@RequestParam String patientId) {
    return ResponseEntity.ok(appointmentService.getAppointmentsForPatient(patientId));
  }
}
