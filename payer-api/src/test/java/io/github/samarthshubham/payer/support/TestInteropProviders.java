package io.github.samarthshubham.payer.support;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import io.github.samarthshubham.payer.interop.OperationScope;
import io.github.samarthshubham.payer.security.SmartPermission;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Stand-in FHIR providers, registered only in tests, for checking the scope interceptor. */
@TestConfiguration
public class TestInteropProviders {

  public static final String MEMBER_A = "member-a";
  public static final String MEMBER_B = "member-b";
  public static final String CLAIM_A = "claim-a";
  public static final String CLAIM_B = "claim-b";
  public static final String QUESTIONNAIRE = "q1";
  public static final String MAPPED_OPERATION = "$test-submit";
  public static final String UNMAPPED_OPERATION = "$test-unmapped";

  @Bean
  IResourceProvider testPatientProvider() {
    return new PatientProvider();
  }

  @Bean
  IResourceProvider testClaimProvider() {
    return new ClaimProvider();
  }

  @Bean
  IResourceProvider testQuestionnaireProvider() {
    return new QuestionnaireProvider();
  }

  @Bean
  OperationScope testSubmitScope() {
    return new OperationScope(MAPPED_OPERATION, "Claim", SmartPermission.CREATE);
  }

  public static class PatientProvider implements IResourceProvider {

    private static final Map<String, Patient> PATIENTS =
        Map.of(MEMBER_A, patient(MEMBER_A), MEMBER_B, patient(MEMBER_B));

    @Override
    public Class<? extends IBaseResource> getResourceType() {
      return Patient.class;
    }

    @Read
    public Patient read(@IdParam IdType id) {
      return find(PATIENTS, id);
    }

    @Search
    public List<Patient> search() {
      return List.copyOf(PATIENTS.values());
    }

    private static Patient patient(String id) {
      Patient patient = new Patient();
      patient.setId(new IdType("Patient", id));
      return patient;
    }
  }

  public static class ClaimProvider implements IResourceProvider {

    private static final Map<String, Claim> CLAIMS =
        Map.of(CLAIM_A, claim(CLAIM_A, MEMBER_A), CLAIM_B, claim(CLAIM_B, MEMBER_B));

    @Override
    public Class<? extends IBaseResource> getResourceType() {
      return Claim.class;
    }

    @Read
    public Claim read(@IdParam IdType id) {
      return find(CLAIMS, id);
    }

    @Create
    public MethodOutcome create(@ResourceParam Claim claim) {
      return new MethodOutcome(new IdType("Claim", "new-claim"), true);
    }

    @Operation(name = MAPPED_OPERATION)
    public Parameters submit(@ResourceParam Parameters input) {
      return new Parameters();
    }

    @Operation(name = UNMAPPED_OPERATION)
    public Parameters unmapped(@ResourceParam Parameters input) {
      return new Parameters();
    }

    private static Claim claim(String id, String patientId) {
      Claim claim = new Claim();
      claim.setId(new IdType("Claim", id));
      claim.setPatient(new Reference("Patient/" + patientId));
      return claim;
    }
  }

  public static class QuestionnaireProvider implements IResourceProvider {

    @Override
    public Class<? extends IBaseResource> getResourceType() {
      return Questionnaire.class;
    }

    @Read
    public Questionnaire read(@IdParam IdType id) {
      Questionnaire questionnaire = new Questionnaire();
      questionnaire.setId(new IdType("Questionnaire", QUESTIONNAIRE));
      return find(Map.of(QUESTIONNAIRE, questionnaire), id);
    }
  }

  private static <T extends IBaseResource> T find(Map<String, T> resources, IdType id) {
    T resource = resources.get(id.getIdPart());
    if (resource == null) {
      throw new ResourceNotFoundException(id);
    }
    return resource;
  }
}
