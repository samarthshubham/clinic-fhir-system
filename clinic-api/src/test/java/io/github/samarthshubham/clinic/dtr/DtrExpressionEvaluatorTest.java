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
