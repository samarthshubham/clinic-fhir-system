package io.github.samarthshubham.payer.support;

import io.github.samarthshubham.payer.mapper.MemberMapper;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.InsurancePlan;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;

/** Member resources shaped like the E1-S1 seed (payer-members.feature). */
public final class MemberFixtures {

  public static final String GOLD_PLAN_ID = "praxis-gold-ppo";
  public static final String GOLD_PLAN_NAME = "Praxis Gold PPO";
  private static final String COVERAGE_CLASS =
      "http://terminology.hl7.org/CodeSystem/coverage-class";

  private MemberFixtures() {}

  public static Patient member(String id, String memberId) {
    Patient patient = new Patient();
    patient.setId(id);
    if (memberId != null) {
      patient.addIdentifier().setSystem(MemberMapper.MEMBER_ID_SYSTEM).setValue(memberId);
    }
    patient.addIdentifier().setSystem("http://example.org/other").setValue("x");
    patient.addName().setFamily("Nair").addGiven("Priya");
    patient.setBirthDateElement(new DateType("1985-03-14"));
    patient.setGender(Enumerations.AdministrativeGender.FEMALE);
    return patient;
  }

  public static Coverage coverage(
      String id, String memberPatientId, Coverage.CoverageStatus status, String start, String end) {
    Coverage coverage = new Coverage();
    coverage.setId(id);
    coverage.setStatus(status);
    coverage.setBeneficiary(new Reference("Patient/" + memberPatientId));
    coverage
        .getRelationship()
        .addCoding()
        .setSystem("http://terminology.hl7.org/CodeSystem/subscriber-relationship")
        .setCode("self");
    coverage.getPeriod().setStartElement(new DateTimeType(start));
    if (end != null) {
      coverage.getPeriod().setEndElement(new DateTimeType(end));
    }
    coverage.addPayor(new Reference("Organization/php").setDisplay("Praxis Health Plan"));
    Coverage.ClassComponent group = coverage.addClass_().setValue("group-1").setName("Group");
    group.getType().addCoding().setSystem(COVERAGE_CLASS).setCode("group");
    Coverage.ClassComponent plan =
        coverage.addClass_().setValue(GOLD_PLAN_ID).setName(GOLD_PLAN_NAME);
    plan.getType().addCoding().setSystem(COVERAGE_CLASS).setCode("plan");
    return coverage;
  }

  public static InsurancePlan goldPlan() {
    InsurancePlan plan = new InsurancePlan();
    plan.setId("plan-gold");
    plan.addIdentifier().setSystem("http://example.org/other").setValue("x");
    plan.addIdentifier().setSystem(MemberMapper.INSURANCE_PLAN_SYSTEM).setValue(GOLD_PLAN_ID);
    plan.setName(GOLD_PLAN_NAME);
    plan.addPlan().getType().setText("PPO");
    return plan;
  }

  public static Claim preAuth(String id, String memberPatientId, String created) {
    Claim claim = new Claim();
    claim.setId(id);
    claim.setStatus(Claim.ClaimStatus.ACTIVE);
    claim.setUse(Claim.Use.PREAUTHORIZATION);
    claim.setPatient(new Reference("Patient/" + memberPatientId));
    claim.setCreatedElement(new DateTimeType(created));
    claim
        .addItem()
        .getProductOrService()
        .addCoding()
        .setSystem("http://snomed.info/sct")
        .setCode("40701008")
        .setDisplay("Echocardiography");
    return claim;
  }

  public static ClaimResponse decision(String id, String claimId) {
    ClaimResponse response = new ClaimResponse();
    response.setId(id);
    response.setRequest(new Reference("Claim/" + claimId));
    response.setOutcome(ClaimResponse.RemittanceOutcome.COMPLETE);
    response.setPreAuthRef("PA-0001");
    return response;
  }
}
