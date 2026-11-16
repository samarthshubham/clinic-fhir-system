package io.github.samarthshubham.clinic.dtr;

import static io.github.samarthshubham.clinic.support.FhirClientStub.bundleOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.clinic.support.FhirClientStub;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Expression;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DtrExpressionEvaluatorTest {

  private final FhirClientStub fhir = new FhirClientStub();
  private final DtrExpressionEvaluator evaluator =
      new DtrExpressionEvaluator(FhirContext.forR4Cached(), fhir.client());

  private final Map<String, List<Base>> variables =
      Map.of("patient", List.of(patient()), "flag", List.of(new BooleanType(true)));

  @Test
  void fhirPathReadsTheLaunchContextAndOtherVariables() {
    List<Base> result = evaluator.evaluate(fhirPath("iif(%flag, %patient.id, 'no')"), variables);

    assertThat(result).singleElement().extracting(Base::primitiveValue).isEqualTo("p 1");
  }

  @Test
  void fhirQueryRunsAgainstTheProviderServerWithEmbeddedFhirPathUrlEncoded() {
    Bundle conditions = bundleOf(new Condition());
    fhir.givenSearchByUrl("Condition?patient=p+1&clinical-status=active", conditions);

    List<Base> result =
        evaluator.evaluate(
            new Expression()
                .setLanguage(DtrExpressionEvaluator.FHIR_QUERY)
                .setExpression("Condition?patient={{%patient.id}}&clinical-status=active"),
            variables);

    assertThat(result).containsExactly(conditions);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://elsewhere.example/Condition?patient={{%patient.id}}",
        "//elsewhere.example/Condition?patient={{%patient.id}}",
        "/Condition?patient={{%patient.id}}",
        "Condition/1?patient={{%patient.id}}",
        "Patient?_count=100",
        "Condition?clinical-status=active",
        "Condition?patient=other",
        "Condition?patient={{%patient.id}},other",
        "Condition?patient={{%patient.id}}&_include=Condition:asserter",
        "Condition?patient={{%patient.id}}&_revinclude=Provenance:target",
        "Condition?patient={{%patient.id}}&_has:Encounter:patient:status=finished"
      })
  void fhirQueryOutsideTheLaunchPatientsChartIsRejectedWithoutACall(String query) {
    Expression expression =
        new Expression().setLanguage(DtrExpressionEvaluator.FHIR_QUERY).setExpression(query);

    assertThatThrownBy(() -> evaluator.evaluate(expression, variables))
        .isInstanceOf(DtrExpressionEvaluator.UnsupportedExpressionException.class);
    assertThat(fhir.searchedUrls()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Patient?_id={{%patient.id}}",
        "Encounter?subject=Patient/{{%patient.id}}&_sort=date",
        "Observation?patient:Patient={{%patient.id}}&code=8480-6"
      })
  void fhirQueryLimitedToTheLaunchPatientRuns(String query) {
    Bundle bundle = bundleOf(new Condition());
    String url = query.replace("{{%patient.id}}", "p+1");
    fhir.givenSearchByUrl(url, bundle);

    List<Base> result =
        evaluator.evaluate(
            new Expression().setLanguage(DtrExpressionEvaluator.FHIR_QUERY).setExpression(query),
            variables);

    assertThat(result).containsExactly(bundle);
  }

  @Test
  void fhirQueryWithoutALaunchPatientIsRejected() {
    Expression expression =
        new Expression()
            .setLanguage(DtrExpressionEvaluator.FHIR_QUERY)
            .setExpression("Condition?clinical-status=active");

    assertThatThrownBy(() -> evaluator.evaluate(expression, Map.of()))
        .isInstanceOf(DtrExpressionEvaluator.UnsupportedExpressionException.class);
    assertThat(fhir.searchedUrls()).isEmpty();
  }

  @Test
  void unknownVariableIsAnError() {
    assertThatThrownBy(() -> evaluator.evaluate(fhirPath("%missing.exists()"), variables))
        .isInstanceOf(DtrExpressionEvaluator.UnsupportedExpressionException.class)
        .hasMessageContaining("%missing");
  }

  @Test
  void unsupportedLanguageIsAnError() {
    Expression cql =
        new Expression().setLanguage("text/cql").setExpression("\"Primary Diagnosis\"");

    assertThatThrownBy(() -> evaluator.evaluate(cql, variables))
        .isInstanceOf(DtrExpressionEvaluator.UnsupportedExpressionException.class)
        .hasMessageContaining("text/cql");
  }

  @Test
  void fhirPathWithoutPatientEvaluatesLiterals() {
    List<Base> result = evaluator.evaluate(fhirPath("'a' + 'b'"), Map.of());

    assertThat(result).singleElement().isInstanceOf(StringType.class);
    assertThat(result.get(0).primitiveValue()).isEqualTo("ab");
  }

  private static Expression fhirPath(String expression) {
    return new Expression().setLanguage(DtrExpressionEvaluator.FHIRPATH).setExpression(expression);
  }

  private static Patient patient() {
    Patient patient = new Patient();
    patient.setId("p 1");
    return patient;
  }
}
