package io.github.samarthshubham.clinic.controller;

import io.github.samarthshubham.clinic.dto.PageResponse;
import io.github.samarthshubham.clinic.dto.ServiceRequestRequest;
import io.github.samarthshubham.clinic.dto.ServiceRequestResponse;
import io.github.samarthshubham.clinic.service.ServiceRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service-requests")
public class ServiceRequestController {

  private final ServiceRequestService serviceRequestService;

  public ServiceRequestController(ServiceRequestService serviceRequestService) {
    this.serviceRequestService = serviceRequestService;
  }

  @PostMapping
  public ResponseEntity<ServiceRequestResponse> createServiceRequest(
      @Valid @RequestBody ServiceRequestRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(serviceRequestService.createServiceRequest(request));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ServiceRequestResponse> getServiceRequest(
      @PathVariable @NotBlank String id) {
    return ResponseEntity.ok(serviceRequestService.getServiceRequest(id));
  }

  @GetMapping
  public ResponseEntity<PageResponse<ServiceRequestResponse>> getServiceRequestsForPatient(
      @RequestParam @NotBlank String patientId, @RequestParam(required = false) String status) {
    return ResponseEntity.ok(serviceRequestService.getServiceRequestsForPatient(patientId, status));
  }
}
