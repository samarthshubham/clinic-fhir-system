package io.github.samarthshubham.payer.mapper;

import io.github.samarthshubham.payer.dto.PriorAuthQueueItem;
import java.time.Duration;
import java.time.Instant;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Component;

@Component
public class PriorAuthQueueMapper {

  public PriorAuthQueueItem toItem(
      Claim claim,
      ClaimResponse response,
      Patient patient,
      boolean expedited,
      Instant deadline,
      Duration remaining) {
    return new PriorAuthQueueItem(
        claim.getIdPart(),
        response.getPreAuthRef(),
        MemberMapper.memberId(patient),
        patient.getNameFirstRep().getNameAsSingleString(),
        MemberMapper.service(claim.getItemFirstRep().getProductOrService()),
        expedited,
        deadline,
        remaining.getSeconds(),
        remaining.isNegative());
  }
}
