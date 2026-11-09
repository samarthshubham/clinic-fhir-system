package io.github.samarthshubham.payer.service;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.IQuery;
import io.github.samarthshubham.payer.dto.MemberResponse;
import io.github.samarthshubham.payer.dto.MemberSummary;
import io.github.samarthshubham.payer.dto.PageResponse;
import io.github.samarthshubham.payer.dto.PriorAuthSummary;
import io.github.samarthshubham.payer.exception.ResourceNotFoundException;
import io.github.samarthshubham.payer.mapper.MemberMapper;
import io.github.samarthshubham.payer.mapper.PageMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.InsurancePlan;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.stereotype.Service;

@Service
public class MemberService {

  static final int MAX_RESULTS = 100;

  private final IGenericClient fhirClient;
  private final MemberMapper memberMapper;

  public MemberService(IGenericClient fhirClient, MemberMapper memberMapper) {
    this.fhirClient = fhirClient;
    this.memberMapper = memberMapper;
  }

  public PageResponse<MemberSummary> searchMembers(String name, String memberId) {
    IQuery<Bundle> query =
        fhirClient
            .search()
            .forResource(Patient.class)
            .returnBundle(Bundle.class)
            .where(
                isBlank(memberId)
                    ? Patient.IDENTIFIER.hasSystemWithAnyCode(MemberMapper.MEMBER_ID_SYSTEM)
                    : Patient.IDENTIFIER
                        .exactly()
                        .systemAndCode(MemberMapper.MEMBER_ID_SYSTEM, memberId.trim()))
            .revInclude(Coverage.INCLUDE_BENEFICIARY)
            .count(MAX_RESULTS);
    if (!isBlank(name)) {
      query = query.and(Patient.NAME.matches().value(name.trim()));
    }
    Bundle bundle = query.execute();
    Map<String, List<Coverage>> coveragesByMember =
        PageMapper.resources(bundle, Coverage.class).stream()
            .collect(
                Collectors.groupingBy(
                    coverage -> coverage.getBeneficiary().getReferenceElement().getIdPart()));
    return PageMapper.toPage(
        bundle,
        Patient.class,
        patient ->
            memberMapper.toSummary(
                patient,
                coveragesByMember.getOrDefault(patient.getIdElement().getIdPart(), List.of())));
  }

  public MemberResponse getMember(String id) {
    Patient patient = findMember(id);
    List<Coverage> coverages = coverages(id);
    return memberMapper.toResponse(patient, coverages, plans(coverages), priorAuths(id));
  }

  private Patient findMember(String id) {
    Patient patient;
    try {
      patient = fhirClient.read().resource(Patient.class).withId(id).execute();
    } catch (ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException
        | ca.uhn.fhir.rest.server.exceptions.ResourceGoneException e) {
      throw new ResourceNotFoundException("Member", id);
    }
    if (MemberMapper.memberId(patient) == null) {
      throw new ResourceNotFoundException("Member", id);
    }
    return patient;
  }

  private List<Coverage> coverages(String memberId) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(Coverage.class)
            .where(Coverage.BENEFICIARY.hasId("Patient/" + memberId))
            .count(MAX_RESULTS)
            .returnBundle(Bundle.class)
            .execute();
    return PageMapper.resources(bundle, Coverage.class);
  }

  private Map<String, InsurancePlan> plans(List<Coverage> coverages) {
    List<String> planIds =
        coverages.stream().map(MemberMapper::planId).filter(Objects::nonNull).distinct().toList();
    if (planIds.isEmpty()) {
      return Map.of();
    }
    Bundle bundle =
        fhirClient
            .search()
            .forResource(InsurancePlan.class)
            .where(
                InsurancePlan.IDENTIFIER
                    .exactly()
                    .systemAndValues(MemberMapper.INSURANCE_PLAN_SYSTEM, planIds))
            .returnBundle(Bundle.class)
            .execute();
    return PageMapper.resources(bundle, InsurancePlan.class).stream()
        .filter(plan -> MemberMapper.planId(plan) != null)
        .collect(
            Collectors.toMap(MemberMapper::planId, Function.identity(), (first, second) -> first));
  }

  private List<PriorAuthSummary> priorAuths(String memberId) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(Claim.class)
            .where(Claim.PATIENT.hasId("Patient/" + memberId))
            .and(Claim.USE.exactly().code(Claim.Use.PREAUTHORIZATION.toCode()))
            .revInclude(ClaimResponse.INCLUDE_REQUEST)
            .sort()
            .descending(Claim.CREATED)
            .count(MAX_RESULTS)
            .returnBundle(Bundle.class)
            .execute();
    Map<String, ClaimResponse> responsesByClaim =
        PageMapper.resources(bundle, ClaimResponse.class).stream()
            .collect(
                Collectors.toMap(
                    response -> response.getRequest().getReferenceElement().getIdPart(),
                    Function.identity(),
                    (first, second) -> first));
    return PageMapper.resources(bundle, Claim.class).stream()
        .map(
            claim ->
                memberMapper.toPriorAuth(
                    claim, responsesByClaim.get(claim.getIdElement().getIdPart())))
        .toList();
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
