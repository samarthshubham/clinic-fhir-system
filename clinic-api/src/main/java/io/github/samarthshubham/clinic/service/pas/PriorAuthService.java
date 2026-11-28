package io.github.samarthshubham.clinic.service.pas;

import io.github.samarthshubham.clinic.client.PasSubmitClient;
import io.github.samarthshubham.clinic.dto.PriorAuthRequest;
import io.github.samarthshubham.clinic.dto.PriorAuthRequest.BillingCode;
import io.github.samarthshubham.clinic.dto.PriorAuthResponse;
import io.github.samarthshubham.clinic.service.pas.PasBundleBuilder.Billing;
import java.util.List;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.stereotype.Service;

@Service
public class PriorAuthService {

  private static final String SERVICE_CATEGORY_SYSTEM = "https://codesystem.x12.org/005010/1365";

  private final PasRequestService requests;
  private final PasSubmitClient payer;
  private final PasDecisionService decisions;

  public PriorAuthService(
      PasRequestService requests, PasSubmitClient payer, PasDecisionService decisions) {
    this.requests = requests;
    this.payer = payer;
    this.decisions = decisions;
  }

  public PriorAuthResponse submit(PriorAuthRequest request) {
    return decisions.findByOrder(request.orderId()).orElseGet(() -> submitNew(request));
  }

  private PriorAuthResponse submitNew(PriorAuthRequest request) {
    Coding service = request.service() == null ? null : coding(request.service());
    List<Coding> diagnoses =
        request.diagnoses() == null
            ? List.of()
            : request.diagnoses().stream().map(PriorAuthService::coding).toList();
    var billing =
        new Billing(
            service,
            diagnoses,
            new Coding(SERVICE_CATEGORY_SYSTEM, request.serviceCategory(), null),
            request.placeOfService());
    var bundle = requests.build(request.orderId(), billing);
    return decisions.track(bundle, payer.submit(bundle));
  }

  private static Coding coding(BillingCode code) {
    return new Coding(code.system(), code.code(), null);
  }

  public List<PriorAuthResponse> list(String patientId) {
    return decisions.listByPatient(patientId);
  }
}
