package io.github.samarthshubham.clinic.service.dtr;

import io.github.samarthshubham.clinic.service.PatientService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Expression;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DtrPrepopulationService {

  static final String LAUNCH_CONTEXT =
      "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-launchContext";
  static final String VARIABLE = "http://hl7.org/fhir/StructureDefinition/variable";
  static final String INITIAL_EXPRESSION =
      "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression";
  static final String INFORMATION_ORIGIN =
      "http://hl7.org/fhir/us/davinci-dtr/StructureDefinition/information-origin";
  static final String AUTO_CLIENT = "auto-client";
  private static final Set<String> CONTEXT_VARIABLES = Set.of("patient", "questionnaire", "qitem");

  private final PatientService patientService;
  private final DtrExpressionEvaluator evaluator;

  public DtrPrepopulationService(PatientService patientService, DtrExpressionEvaluator evaluator) {
    this.patientService = patientService;
    this.evaluator = evaluator;
  }

  public QuestionnaireResponse prepopulate(Questionnaire questionnaire, String patientId) {
    Patient patient = patientService.findPatient(patientId).copy();
    patient.setId(patient.getIdElement().getIdPart());

    Map<String, List<Base>> scope = new HashMap<>();
    scope.put("questionnaire", List.of(questionnaire));
    if (hasPatientLaunchContext(questionnaire)) {
      scope.put("patient", List.of(patient));
    }
    Evaluation evaluation = new Evaluation(questionnaire.getIdElement().getIdPart());
    evaluation.addVariables(questionnaire, scope, null);

    QuestionnaireResponse response = new QuestionnaireResponse();
    response.setQuestionnaire(questionnaire.getUrl());
    response.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS);
    response.setSubject(new Reference("Patient/" + patient.getIdPart()));
    response.setItem(evaluation.items(questionnaire.getItem(), scope));
    return response;
  }

  private static boolean hasPatientLaunchContext(Questionnaire questionnaire) {
    return questionnaire.getExtensionsByUrl(LAUNCH_CONTEXT).stream()
        .map(context -> context.getExtensionByUrl("name"))
        .anyMatch(
            name ->
                name != null
                    && name.getValue() instanceof Coding coding
                    && "patient".equals(coding.getCode()));
  }

  private final class Evaluation {

    private final String questionnaireId;

    Evaluation(String questionnaireId) {
      this.questionnaireId = questionnaireId;
    }

    void addVariables(DomainResource owner, Map<String, List<Base>> scope, String linkId) {
      addVariables(owner.getExtensionsByUrl(VARIABLE), scope, linkId);
    }

    private void addVariables(
        List<Extension> variables, Map<String, List<Base>> scope, String linkId) {
      for (Extension variable : variables) {
        if (!(variable.getValue() instanceof Expression expression) || !expression.hasName()) {
          continue;
        }
        if (CONTEXT_VARIABLES.contains(expression.getName())) {
          logFailure(
              "variable " + expression.getName(),
              linkId,
              new IllegalArgumentException("Context variables cannot be redefined"));
          continue;
        }
        try {
          scope.put(expression.getName(), evaluator.evaluate(expression, scope));
        } catch (RuntimeException e) {
          scope.put(expression.getName(), List.of());
          logFailure("variable " + expression.getName(), linkId, e);
        }
      }
    }

    List<QuestionnaireResponseItemComponent> items(
        List<QuestionnaireItemComponent> questions, Map<String, List<Base>> parentScope) {
      List<QuestionnaireResponseItemComponent> items = new ArrayList<>();
      for (QuestionnaireItemComponent question : questions) {
        if (question.getType() == Questionnaire.QuestionnaireItemType.DISPLAY) {
          continue;
        }
        Map<String, List<Base>> scope = new HashMap<>(parentScope);
        scope.put("qitem", List.of(question));
        addVariables(question.getExtensionsByUrl(VARIABLE), scope, question.getLinkId());

        QuestionnaireResponseItemComponent item =
            new QuestionnaireResponseItemComponent()
                .setLinkId(question.getLinkId())
                .setText(question.getText());
        if (question.getType() == Questionnaire.QuestionnaireItemType.GROUP) {
          item.setItem(items(question.getItem(), scope));
        } else {
          initialAnswers(question, scope).forEach(item::addAnswer);
        }
        items.add(item);
      }
      return items;
    }

    private List<QuestionnaireResponseItemAnswerComponent> initialAnswers(
        QuestionnaireItemComponent question, Map<String, List<Base>> scope) {
      Extension initial = question.getExtensionByUrl(INITIAL_EXPRESSION);
      if (initial == null || !(initial.getValue() instanceof Expression expression)) {
        return List.of();
      }
      try {
        List<Base> results = evaluator.evaluate(expression, scope);
        List<Base> used = question.getRepeats() ? results : results.stream().limit(1).toList();
        List<QuestionnaireResponseItemAnswerComponent> answers = new ArrayList<>();
        for (Base result : used) {
          answers.add(prefilled(answerValue(question.getType(), result)));
        }
        return answers;
      } catch (RuntimeException e) {
        logFailure("initialExpression", question.getLinkId(), e);
        return List.of();
      }
    }

    private void logFailure(String what, String linkId, RuntimeException e) {
      log.warn(
          "DTR prepopulation: {} failed (questionnaire {}, linkId {}): {}",
          what,
          questionnaireId,
          linkId,
          e.getClass().getSimpleName());
    }
  }

  private static QuestionnaireResponseItemAnswerComponent prefilled(Type value) {
    QuestionnaireResponseItemAnswerComponent answer =
        new QuestionnaireResponseItemAnswerComponent().setValue(value);
    Extension origin = answer.addExtension().setUrl(INFORMATION_ORIGIN);
    origin.addExtension("source", new CodeType(AUTO_CLIENT));
    return answer;
  }

  static Type answerValue(Questionnaire.QuestionnaireItemType type, Base result) {
    Type value =
        switch (type) {
          case BOOLEAN -> result instanceof BooleanType ? (Type) result : null;
          case INTEGER -> result instanceof IntegerType ? (Type) result : null;
          case DECIMAL ->
              result instanceof DecimalType || result instanceof IntegerType
                  ? new DecimalType(((PrimitiveType<?>) result).getValueAsString())
                  : null;
          case DATE ->
              result instanceof BaseDateTimeType date
                  ? new DateType(
                      date.getValueAsString()
                          .substring(0, Math.min(date.getValueAsString().length(), 10)))
                  : null;
          case DATETIME ->
              result instanceof BaseDateTimeType date
                  ? new DateTimeType(date.getValueAsString())
                  : null;
          case STRING, TEXT ->
              result instanceof PrimitiveType<?> primitive
                  ? new StringType(primitive.getValueAsString())
                  : null;
          case CHOICE -> result instanceof Coding ? (Type) result : null;
          case OPENCHOICE ->
              result instanceof Coding
                  ? (Type) result
                  : result instanceof PrimitiveType<?> primitive
                      ? new StringType(primitive.getValueAsString())
                      : null;
          default -> null;
        };
    if (value == null) {
      throw new IllegalArgumentException(
          "Result type " + result.fhirType() + " does not fit item type " + type.toCode());
    }
    return value.copy();
  }
}
