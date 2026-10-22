package io.github.samarthshubham.clinic.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import io.github.samarthshubham.clinic.dto.PractitionerRequest;
import io.github.samarthshubham.clinic.support.TestAuth;
import org.junit.jupiter.api.Test;

class StaffAccountBootstrapTest {

  private final PractitionerService practitionerService = mock(PractitionerService.class);
  private final StaffAccountBootstrap bootstrap =
      new StaffAccountBootstrap(TestAuth.properties(), practitionerService);

  @Test
  void upsertsOnePractitionerPerConfiguredUser() {
    bootstrap.upsertStaffPractitionersOnStartup();

    verify(practitionerService)
        .upsertStaffPractitioner(
            "admin", new PractitionerRequest("Alex", "Morgan", "Admin", null, null));
    verify(practitionerService)
        .upsertStaffPractitioner(
            "clinician",
            new PractitionerRequest("Anika", "Patel", "Internal Medicine", null, null));
    verify(practitionerService)
        .upsertStaffPractitioner(
            "frontdesk", new PractitionerRequest("Jordan", "Lee", "Front desk", null, null));
  }

  @Test
  void unreachableFhirServerDoesNotStopStartup() {
    doThrow(new FhirClientConnectionException("down"))
        .when(practitionerService)
        .upsertStaffPractitioner(eq("admin"), any());

    assertThatCode(bootstrap::upsertStaffPractitionersOnStartup).doesNotThrowAnyException();
  }
}
