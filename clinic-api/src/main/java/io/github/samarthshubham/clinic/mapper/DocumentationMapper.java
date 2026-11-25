package io.github.samarthshubham.clinic.mapper;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.parser.DataFormatException;
import io.github.samarthshubham.clinic.client.DtrPackage;
import io.github.samarthshubham.clinic.dto.DocumentationAnswer;
import io.github.samarthshubham.clinic.dto.DocumentationCoding;
import io.github.samarthshubham.clinic.dto.DocumentationEnableWhen;
import io.github.samarthshubham.clinic.dto.DocumentationItem;
import io.github.samarthshubham.clinic.dto.DocumentationResponse;
import io.github.samarthshubham.clinic.dto.DocumentationValue;
import io.github.samarthshubham.clinic.exception.DocumentationValidationException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Component;

@Component
public class DocumentationMapper {

  private static final String DTR = "http://hl7.org/fhir/us/davinci-dtr/StructureDefinition/";
  public static final String QUESTIONNAIRE_RESPONSE_PROFILE = DTR + "dtr-questionnaireresponse";
  public static final String QR_COVERAGE = DTR + "qr-coverage";
  public static final String QR_CONTEXT = DTR + "qr-context";
  public static final String INTENDED_USE = DTR + "intendedUse";
  public static final String INFORMATION_ORIGIN = DTR + "information-origin";
  public static final String AUTO_CLIENT = "auto-client";
  public static final String OVERRIDE = "override";
  public static final String MANUAL = "manual";
  private static final Set<String> PREFILLED_ORIGINS = Set.of(AUTO_CLIENT, "auto-server");
  private static final String COVERAGE_INFORMATION_CODES =
      "http://hl7.org/fhir/us/davinci-crd/CodeSystem/coverage-information-codes";

  public DocumentationResponse toResponse(
      String serviceRequestId, DtrPackage questionnairePackage, QuestionnaireResponse response) {
    Questionnaire questionnaire = questionnairePackage.questionnaire();
    return new DocumentationResponse(
        serviceRequestId,
        response.getIdElement().getIdPart(),
        questionnaire.getUrl(),
        questionnaire.hasTitle() ? questionnaire.getTitle() : questionnaire.getName(),
        response.hasStatus() ? response.getStatus().toCode() : null,
        items(questionnaire.getItem(), answersByLinkId(response), valueSets(questionnairePackage)));
  }

  /**
   * Starts the response stored for an order: the DTR profile, the order and Coverage it documents,
   * and the payer's intended use from the package (PA by default). Items are added by the caller.
   */
  public QuestionnaireResponse toFhir(
      String serviceRequestId,
      String patientId,
      String coverageId,
      String authorPractitionerId,
      DtrPackage questionnairePackage,
      QuestionnaireResponse.QuestionnaireResponseStatus status,
      Date authored) {
    Questionnaire questionnaire = questionnairePackage.questionnaire();
    QuestionnaireResponse response = new QuestionnaireResponse();
    response.getMeta().addProfile(QUESTIONNAIRE_RESPONSE_PROFILE);
    response.addExtension(QR_COVERAGE, new Reference("Coverage/" + coverageId));
    intendedUse(questionnairePackage.response()).forEach(response::addExtension);
    response.addExtension(QR_CONTEXT, new Reference("ServiceRequest/" + serviceRequestId));
    response.setQuestionnaire(
        questionnaire.hasVersion()
            ? questionnaire.getUrl() + "|" + questionnaire.getVersion()
            : questionnaire.getUrl());
    response.setStatus(status);
    response.setSubject(new Reference("Patient/" + patientId));
    response.setAuthoredElement(
        new DateTimeType(authored, TemporalPrecisionEnum.SECOND, TimeZone.getTimeZone("UTC")));
    if (authorPractitionerId != null) {
      response.setAuthor(new Reference("Practitioner/" + authorPractitionerId));
    }
    return response;
  }

  /** The FHIR answer value for a DTO value, checked against the question's type and options. */
  public Type toAnswerValue(
      QuestionnaireItemComponent question,
      DocumentationValue value,
      DtrPackage questionnairePackage) {
    String linkId = question.getLinkId();
    long given =
        Stream.of(
                value.valueString(),
                value.valueInteger(),
                value.valueDecimal(),
                value.valueBoolean(),
                value.valueDate(),
                value.valueDateTime(),
                value.valueCoding())
            .filter(Objects::nonNull)
            .count();
    if (given != 1) {
      throw new DocumentationValidationException(
          "Each answer to '" + linkId + "' needs exactly one value");
    }
    QuestionnaireItemType type = question.getType();
    try {
      Type answer =
          switch (type) {
            case BOOLEAN ->
                value.valueBoolean() == null ? null : new BooleanType(value.valueBoolean());
            case INTEGER ->
                value.valueInteger() == null ? null : new IntegerType(value.valueInteger());
            case DECIMAL ->
                value.valueDecimal() != null
                    ? new DecimalType(value.valueDecimal())
                    : value.valueInteger() != null ? new DecimalType(value.valueInteger()) : null;
            case DATE -> value.valueDate() == null ? null : new DateType(value.valueDate());
            case DATETIME ->
                value.valueDateTime() == null ? null : new DateTimeType(value.valueDateTime());
            case STRING, TEXT -> text(value);
            case CHOICE ->
                value.valueCoding() == null
                    ? null
                    : option(question, value.valueCoding(), questionnairePackage);
            case OPENCHOICE ->
                value.valueCoding() != null
                    ? option(question, value.valueCoding(), questionnairePackage)
                    : text(value);
            default ->
                throw new DocumentationValidationException(
                    "Item '" + linkId + "' of type " + type.toCode() + " takes no answers");
          };
      if (answer == null) {
        throw new DocumentationValidationException(
            "The answer to '" + linkId + "' must be a " + type.toCode() + " value");
      }
      return answer;
    } catch (DataFormatException | IllegalArgumentException e) {
      throw new DocumentationValidationException(
          "The answer to '" + linkId + "' is not a valid " + type.toCode() + " value");
    }
  }

  public static Map<String, List<QuestionnaireResponseItemAnswerComponent>> answersByLinkId(
      QuestionnaireResponse response) {
    Map<String, List<QuestionnaireResponseItemAnswerComponent>> answers = new HashMap<>();
    addAnswers(response.getItem(), answers);
    return answers;
  }

  public static String origin(QuestionnaireResponseItemAnswerComponent answer) {
    Extension origin = answer.getExtensionByUrl(INFORMATION_ORIGIN);
    Extension source = origin == null ? null : origin.getExtensionByUrl("source");
    return source == null || source.getValue() == null ? null : source.getValue().primitiveValue();
  }

  public static Extension origin(String source, String authorPractitionerId) {
    Extension origin = new Extension(INFORMATION_ORIGIN);
    origin.addExtension("source", new CodeType(source));
    if (authorPractitionerId != null) {
      origin
          .addExtension()
          .setUrl("author")
          .addExtension("practitioner", new Reference("Practitioner/" + authorPractitionerId));
    }
    return origin;
  }

  private static void addAnswers(
      List<QuestionnaireResponseItemComponent> items,
      Map<String, List<QuestionnaireResponseItemAnswerComponent>> answers) {
    for (QuestionnaireResponseItemComponent item : items) {
      if (item.hasAnswer()) {
        answers.computeIfAbsent(item.getLinkId(), k -> new ArrayList<>()).addAll(item.getAnswer());
      }
      addAnswers(item.getItem(), answers);
    }
  }

  private static List<Extension> intendedUse(QuestionnaireResponse packageResponse) {
    List<Extension> fromPayer =
        packageResponse == null ? List.of() : packageResponse.getExtensionsByUrl(INTENDED_USE);
    if (!fromPayer.isEmpty()) {
      return fromPayer.stream().map(Extension::copy).toList();
    }
    return List.of(
        new Extension(
            INTENDED_USE,
            new CodeableConcept(
                new Coding(
                    COVERAGE_INFORMATION_CODES, "withpa", "Include in prior authorization"))));
  }

  private static Type text(DocumentationValue value) {
    if (value.valueString() == null) {
      return null;
    }
    if (value.valueString().isBlank()) {
      throw new IllegalArgumentException("Blank text");
    }
    return new StringType(value.valueString());
  }

  private Coding option(
      QuestionnaireItemComponent question,
      DocumentationCoding coding,
      DtrPackage questionnairePackage) {
    return options(question, valueSets(questionnairePackage)).stream()
        .map(DocumentationValue::valueCoding)
        .filter(Objects::nonNull)
        .filter(
            option ->
                Objects.equals(option.system(), coding.system())
                    && Objects.equals(option.code(), coding.code()))
        .findFirst()
        .map(option -> new Coding(option.system(), option.code(), option.display()))
        .orElseThrow(
            () ->
                new DocumentationValidationException(
                    "'"
                        + coding.code()
                        + "' is not an answer option of '"
                        + question.getLinkId()
                        + "'"));
  }

  private List<DocumentationItem> items(
      List<QuestionnaireItemComponent> questions,
      Map<String, List<QuestionnaireResponseItemAnswerComponent>> answers,
      Map<String, ValueSet> valueSets) {
    return questions.stream()
        .map(
            question ->
                new DocumentationItem(
                    question.getLinkId(),
                    question.getText(),
                    question.hasType() ? question.getType().toCode() : null,
                    question.getRequired(),
                    question.getRepeats(),
                    question.getReadOnly(),
                    options(question, valueSets),
                    enableWhen(question),
                    question.hasEnableBehavior() ? question.getEnableBehavior().toCode() : null,
                    answers.getOrDefault(question.getLinkId(), List.of()).stream()
                        .map(
                            answer ->
                                new DocumentationAnswer(
                                    toValue(answer.getValue()),
                                    PREFILLED_ORIGINS.contains(origin(answer))))
                        .toList(),
                    items(question.getItem(), answers, valueSets)))
        .toList();
  }

  private static List<DocumentationEnableWhen> enableWhen(QuestionnaireItemComponent question) {
    return question.getEnableWhen().stream()
        .map(
            condition ->
                new DocumentationEnableWhen(
                    condition.getQuestion(),
                    condition.hasOperator() ? condition.getOperator().toCode() : null,
                    toValue(condition.getAnswer())))
        .toList();
  }

  private static List<DocumentationValue> options(
      QuestionnaireItemComponent question, Map<String, ValueSet> valueSets) {
    if (question.hasAnswerOption()) {
      return question.getAnswerOption().stream().map(option -> toValue(option.getValue())).toList();
    }
    if (!question.hasAnswerValueSet()) {
      return List.of();
    }
    ValueSet valueSet = valueSets.get(withoutVersion(question.getAnswerValueSet()));
    if (valueSet == null) {
      return List.of();
    }
    if (valueSet.getExpansion().hasContains()) {
      return valueSet.getExpansion().getContains().stream()
          .map(c -> coding(c.getSystem(), c.getCode(), c.getDisplay()))
          .toList();
    }
    return valueSet.getCompose().getInclude().stream()
        .flatMap(
            include ->
                include.getConcept().stream()
                    .map(c -> coding(include.getSystem(), c.getCode(), c.getDisplay())))
        .toList();
  }

  private static Map<String, ValueSet> valueSets(DtrPackage questionnairePackage) {
    Map<String, ValueSet> byUrl = new HashMap<>();
    questionnairePackage.valueSets().forEach(valueSet -> byUrl.put(valueSet.getUrl(), valueSet));
    return byUrl;
  }

  private static String withoutVersion(String canonical) {
    int bar = canonical.indexOf('|');
    return bar < 0 ? canonical : canonical.substring(0, bar);
  }

  private static DocumentationValue coding(String system, String code, String display) {
    return new DocumentationValue(
        null, null, null, null, null, null, new DocumentationCoding(system, code, display));
  }

  static DocumentationValue toValue(Type value) {
    return switch (value) {
      case BooleanType b ->
          new DocumentationValue(null, null, null, b.getValue(), null, null, null);
      case IntegerType i ->
          new DocumentationValue(null, i.getValue(), null, null, null, null, null);
      case DecimalType d ->
          new DocumentationValue(null, null, d.getValue(), null, null, null, null);
      case DateType d ->
          new DocumentationValue(null, null, null, null, d.getValueAsString(), null, null);
      case DateTimeType d ->
          new DocumentationValue(null, null, null, null, null, d.getValueAsString(), null);
      case Coding c -> coding(c.getSystem(), c.getCode(), c.getDisplay());
      case StringType s -> new DocumentationValue(s.getValue(), null, null, null, null, null, null);
      case null, default -> new DocumentationValue(null, null, null, null, null, null, null);
    };
  }
}
