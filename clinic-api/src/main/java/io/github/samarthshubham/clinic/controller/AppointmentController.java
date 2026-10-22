package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.AppointmentRequest;
import io.github.samarthshubham.clinic.dto.AppointmentResponse;
import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.service.AppointmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

  @PreAuthorize("hasAuthority('appointment:write')")
  @PostMapping
  public ResponseEntity<AppointmentResponse> createAppointment(
      @Valid @RequestBody AppointmentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(appointmentService.createAppointment(request));
  }

  @PreAuthorize("hasAuthority('appointment:read')")
  @GetMapping
  public ResponseEntity<PageResponse<AppointmentResponse>> getAppointmentsForPatient(
      @RequestParam @NotBlank String patientId) {
    return ResponseEntity.ok(appointmentService.getAppointmentsForPatient(patientId));
  }
}
