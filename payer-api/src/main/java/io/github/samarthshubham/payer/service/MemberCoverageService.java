package io.github.samarthshubham.payer.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.github.samarthshubham.payer.mapper.MemberMapper;
import io.github.samarthshubham.payer.mapper.PageMapper;
import java.time.LocalDate;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.Period;
import org.springframework.stereotype.Service;

@Service
public class MemberCoverageService {

  private final IGenericClient fhirClient;

  public MemberCoverageService(IGenericClient fhirClient) {
    this.fhirClient = fhirClient;
  }

  public MemberCoverageCheck check(String memberId, LocalDate on) {
    if (memberId == null || memberId.isBlank()) {
      return new MemberCoverageCheck(MemberCoverageCheck.Status.NO_MEMBER_FOUND, null);
    }
    Bundle bundle =
        fhirClient
            .search()
            .forResource(Coverage.class)
            .where(
                Coverage.IDENTIFIER
                    .exactly()
                    .systemAndCode(MemberMapper.MEMBER_ID_SYSTEM, memberId.trim()))
            .count(MemberService.MAX_RESULTS)
            .returnBundle(Bundle.class)
            .execute();
    List<Coverage> coverages = PageMapper.resources(bundle, Coverage.class);
    if (coverages.isEmpty()) {
      return new MemberCoverageCheck(MemberCoverageCheck.Status.NO_MEMBER_FOUND, null);
    }
    return coverages.stream()
        .filter(coverage -> coverage.getStatus() == Coverage.CoverageStatus.ACTIVE)
        .filter(coverage -> covers(coverage.getPeriod(), on))
        .findFirst()
        .map(coverage -> new MemberCoverageCheck(MemberCoverageCheck.Status.ACTIVE, id(coverage)))
        .orElseGet(
            () ->
                new MemberCoverageCheck(
                    MemberCoverageCheck.Status.NO_ACTIVE_COVERAGE, id(coverages.get(0))));
  }

  private static boolean covers(Period period, LocalDate on) {
    LocalDate start = MemberMapper.date(period.getStartElement());
    LocalDate end = MemberMapper.date(period.getEndElement());
    return (start == null || !on.isBefore(start)) && (end == null || !on.isAfter(end));
  }

  private static String id(Coverage coverage) {
    return coverage.getIdElement().getIdPart();
  }
}
