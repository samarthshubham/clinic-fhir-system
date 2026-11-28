package io.github.samarthshubham.clinic.service.pas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.clinic.client.PasSubmitClient;
import io.github.samarthshubham.clinic.dto.PriorAuthRequest;
import io.github.samarthshubham.clinic.dto.PriorAuthRequest.BillingCode;
import io.github.samarthshubham.clinic.dto.PriorAuthResponse;
import io.github.samarthshubham.clinic.dto.PriorAuthResponse.Status;
import io.github.samarthshubham.clinic.exception.PasSubmissionUnavailableException;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PriorAuthServiceTest {

  private final PasRequestService requests = mock(PasRequestService.class);
  private final PasSubmitClient payer = mock(PasSubmitClient.class);
  private final PasDecisionService decisions = mock(PasDecisionService.class);
  private final PriorAuthService service = new PriorAuthService(requests, payer, decisions);
  private final PriorAuthResponse pended =
      new PriorAuthResponse("order-1", Status.PENDED, null, null, null);

  @Test
  void submitsTheSavedOrderWithBillingThenTracksThePayerDecision() {
    Bundle bundle = new Bundle();
    ClaimResponse initial = new ClaimResponse();
    when(requests.build(eq("order-1"), any())).thenReturn(bundle);
    when(payer.submit(bundle)).thenReturn(initial);
    when(decisions.track(bundle, initial)).thenReturn(pended);
    var request =
        new PriorAuthRequest(
            "order-1",
            new BillingCode("http://www.ama-assn.org/go/cpt", "93015"),
            "11",
            "1",
            List.of(new BillingCode("http://hl7.org/fhir/sid/icd-10-cm", "R00.0")));

    assertThat(service.submit(request)).isEqualTo(pended);

    var billing = ArgumentCaptor.forClass(PasBundleBuilder.Billing.class);
    verify(requests).build(eq("order-1"), billing.capture());
    assertThat(billing.getValue().placeOfService()).isEqualTo("11");
    assertThat(billing.getValue().category().getSystem())
        .isEqualTo("https://codesystem.x12.org/005010/1365");
    assertThat(billing.getValue().service().getCode()).isEqualTo("93015");
    assertThat(billing.getValue().diagnoses())
        .singleElement()
        .satisfies(code -> assertThat(code.getCode()).isEqualTo("R00.0"));
    verify(decisions).track(bundle, initial);
  }

  @Test
  void resubmissionReturnsCurrentStateWithoutRebuildingThePayerRequest() {
    var approved = new PriorAuthResponse("order-1", Status.APPROVED, "PA-1", null, null);
    when(decisions.findByOrder("order-1")).thenReturn(Optional.of(approved));

    assertThat(service.submit(new PriorAuthRequest("order-1", null, "11", "1", null)))
        .isEqualTo(approved);

    verifyNoInteractions(requests, payer);
  }

  @Test
  void aFailedSubmitDoesNotSaveAnUnconfirmedDecision() {
    var bundle = new Bundle();
    when(requests.build(eq("order-1"), any())).thenReturn(bundle);
    when(payer.submit(bundle)).thenThrow(new PasSubmissionUnavailableException("offline"));

    assertThatThrownBy(() -> service.submit(new PriorAuthRequest("order-1", null, "11", "1", null)))
        .isInstanceOf(PasSubmissionUnavailableException.class);

    verify(decisions).findByOrder("order-1");
    org.mockito.Mockito.verifyNoMoreInteractions(decisions);
  }
}
