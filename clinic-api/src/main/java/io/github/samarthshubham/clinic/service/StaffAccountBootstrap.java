package io.github.samarthshubham.clinic.service;

import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import io.github.samarthshubham.clinic.config.AuthProperties;
import io.github.samarthshubham.clinic.dto.PractitionerRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StaffAccountBootstrap {

  private final AuthProperties authProperties;
  private final PractitionerService practitionerService;

  public StaffAccountBootstrap(
      AuthProperties authProperties, PractitionerService practitionerService) {
    this.authProperties = authProperties;
    this.practitionerService = practitionerService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void upsertStaffPractitionersOnStartup() {
    try {
      for (AuthProperties.User user : authProperties.users()) {
        practitionerService.upsertStaffPractitioner(
            user.username(),
            new PractitionerRequest(user.firstName(), user.lastName(), user.title(), null, null));
      }
      log.info("Upserted {} staff practitioners", authProperties.users().size());
    } catch (BaseServerResponseException e) {
      log.warn("Staff practitioners not upserted, logins fall back to config: {}", e.getMessage());
    }
  }
}
