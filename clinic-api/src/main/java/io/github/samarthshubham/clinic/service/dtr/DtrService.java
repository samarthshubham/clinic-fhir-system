package io.github.samarthshubham.clinic.service.dtr;

import static io.github.samarthshubham.clinic.mapper.DocumentationMapper.MANUAL;
import static io.github.samarthshubham.clinic.mapper.DocumentationMapper.OVERRIDE;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.ReferenceClientParam;
import io.github.samarthshubham.clinic.client.DtrClient;
import io.github.samarthshubham.clinic.client.DtrPackage;
import io.github.samarthshubham.clinic.dto.DocumentationItemAnswers;
import io.github.samarthshubham.clinic.dto.DocumentationRequest;
import io.github.samarthshubham.clinic.dto.DocumentationResponse;
import io.github.samarthshubham.clinic.dto.DocumentationValue;
import io.github.samarthshubham.clinic.exception.DocumentationConflictException;
import io.github.samarthshubham.clinic.exception.DocumentationNotFoundException;
import io.github.samarthshubham.clinic.exception.DocumentationValidationException;
import io.github.samarthshubham.clinic.mapper.DocumentationMapper;
import io.github.samarthshubham.clinic.service.CrdService;
import io.github.samarthshubham.clinic.service.ServiceRequestService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemEnableWhenComponent;
import org.hl7.fhir.r4.model.Questionnaire.QuestionnaireItemType;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemAnswerComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseStatus;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Type;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * DTR documentation for an order. The QuestionnaireResponse is stored in the provider HAPI and
 * linked to the order with the DTR {@code qr-context} extension (the DTR profile forbids {@code
 * basedOn}).
 */
@Service
public class DtrService {

  static final int MAX_RESPONSES = 100;

  private final IGenericClient fhirClient;
  private final ServiceRequestService serviceRequestService;
  private final CrdService crdService;
  private final DtrClient dtrClient;
  private final DtrPrepopulationService prepopulationService;
  private final DocumentationMapper documentationMapper;
  private final Clock clock;

  @Autowired
  public DtrService(
      IGenericClient fhirClient,
      ServiceRequestService serviceRequestService,
      CrdService crdService,
      DtrClient dtrClient,
      DtrPrepopulationService prepopulationService,
      DocumentationMapper documentationMapper) {
    this(
        fhirClient,
        serviceRequestService,
        crdService,
        dtrClient,
        prepopulationService,
        documentationMapper,
        Clock.systemUTC());
  }

  DtrService(
      IGenericClient fhirClient,
      ServiceRequestService serviceRequestService,
      CrdService crdService,
      DtrClient dtrClient,
      DtrPrepopulationService prepopulationService,
      DocumentationMapper documentationMapper,
      Clock clock) {
    this.fhirClient = fhirClient;
    this.serviceRequestService = serviceRequestService;
    this.crdService = crdService;
    this.dtrClient = dtrClient;
    this.prepopulationService = prepopulationService;
    this.documentationMapper = documentationMapper;
    this.clock = clock;
  }

  /** The saved response for the order, or a new one prefilled from the chart (not saved). */
  public DocumentationResponse getDocumentation(String serviceRequestId) {
    Form form = load(serviceRequestId);
    return documentationMapper.toResponse(
        serviceRequestId, form.questionnairePackage(), current(form));
  }

  public DocumentationResponse saveDocumentation(
      String serviceRequestId, DocumentationRequest request, String practitionerId) {
    return save(serviceRequestId, request, practitionerId, QuestionnaireResponseStatus.INPROGRESS);
  }

  public DocumentationResponse completeDocumentation(
      String serviceRequestId, DocumentationRequest request, String practitionerId) {
    return save(serviceRequestId, request, practitionerId, QuestionnaireResponseStatus.COMPLETED);
  }

  private DocumentationResponse save(
      String serviceRequestId,
      DocumentationRequest request,
      String practitionerId,
      QuestionnaireResponseStatus status) {
    Form form = load(serviceRequestId);
    if (form.saved() != null && form.saved().getStatus() == QuestionnaireResponseStatus.COMPLETED) {
      throw new DocumentationConflictException(
          "The documentation for ServiceRequest '" + serviceRequestId + "' is already completed");
    }
    String author = practitionerId != null ? practitionerId : requesterId(form.order());
    Questionnaire questionnaire = form.questionnairePackage().questionnaire();

    Map<String, List<QuestionnaireResponseItemAnswerComponent>> previous =
        DocumentationMapper.answersByLinkId(current(form));
    Map<String, List<QuestionnaireResponseItemAnswerComponent>> answers =
        new LinkedHashMap<>(previous);
    Map<String, QuestionnaireItemComponent> questions = new HashMap<>();
    collectQuestions(questionnaire.getItem(), questions);
    List<DocumentationItemAnswers> changes = request == null ? List.of() : request.answers();
    for (DocumentationItemAnswers change : changes) {
      QuestionnaireItemComponent question = questions.get(change.linkId());
      if (question == null) {
        throw new DocumentationValidationException(
            "The form has no question '" + change.linkId() + "'");
      }
      if (!question.getRepeats() && change.values().size() > 1) {
        throw new DocumentationValidationException(
            "Question '" + change.linkId() + "' takes one answer");
      }
      List<QuestionnaireResponseItemAnswerComponent> next = new ArrayList<>();
      for (DocumentationValue value : change.values()) {
        Type answerValue =
            documentationMapper.toAnswerValue(question, value, form.questionnairePackage());
        QuestionnaireResponseItemAnswerComponent answer =
            new QuestionnaireResponseItemAnswerComponent().setValue(answerValue);
        answer.addExtension(
            origin(
                previous.getOrDefault(change.linkId(), List.of()),
                answerValue,
                author,
                change.linkId()));
        next.add(answer);
      }
      answers.put(change.linkId(), next);
    }

    Set<String> enabled = new HashSet<>();
    dropDisabled(questionnaire.getItem(), answers, enabled, true);
    if (status == QuestionnaireResponseStatus.COMPLETED) {
      List<String> missing = new ArrayList<>();
      collectMissing(questionnaire.getItem(), answers, enabled, missing);
      if (!missing.isEmpty()) {
        throw new DocumentationValidationException(
            "Required questions are not answered: " + String.join(", ", missing));
      }
    }

    QuestionnaireResponse response =
        documentationMapper.toFhir(
            serviceRequestId,
            form.patientId(),
            form.coverage().getIdPart(),
            author,
            form.questionnairePackage(),
            status,
            Date.from(clock.instant()));
    response.setItem(responseItems(questionnaire.getItem(), answers));
    if (form.saved() == null) {
      response.setId(fhirClient.create().resource(response).execute().getId().getIdPart());
    } else {
      response.setId(form.saved().getIdElement().getIdPart());
      fhirClient.update().resource(response).execute();
    }
    return documentationMapper.toResponse(serviceRequestId, form.questionnairePackage(), response);
  }

  private Form load(String serviceRequestId) {
    ServiceRequest order = serviceRequestService.findServiceRequest(serviceRequestId);
    String patientId = order.getSubject().getReferenceElement().getIdPart();
    Coverage coverage = crdService.activeCoverageWithPayer(patientId);
    if (coverage == null) {
      throw new DocumentationNotFoundException(
          "ServiceRequest '"
              + serviceRequestId
              + "' has no payer documentation: the patient has no active coverage with the payer");
    }
    DtrPackage questionnairePackage =
        dtrClient
            .questionnairePackage(order, coverage)
            .orElseThrow(
                () ->
                    new DocumentationNotFoundException(
                        "The payer needs no documentation for ServiceRequest '"
                            + serviceRequestId
                            + "'"));
    return new Form(
        order, patientId, coverage, questionnairePackage, findSaved(serviceRequestId, patientId));
  }

  private QuestionnaireResponse current(Form form) {
    return form.saved() != null
        ? form.saved()
        : prepopulationService.prepopulate(
            form.questionnairePackage().questionnaire(), form.patientId());
  }

  private QuestionnaireResponse findSaved(String serviceRequestId, String patientId) {
    Bundle bundle =
        fhirClient
            .search()
            .forResource(QuestionnaireResponse.class)
            .where(new ReferenceClientParam("subject").hasId("Patient/" + patientId))
            .sort()
            .descending("_lastUpdated")
            .count(MAX_RESPONSES)
            .returnBundle(Bundle.class)
            .execute();
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(QuestionnaireResponse.class::isInstance)
        .map(QuestionnaireResponse.class::cast)
        .filter(response -> documents(response, serviceRequestId))
        .findFirst()
        .orElse(null);
  }

  private static boolean documents(QuestionnaireResponse response, String serviceRequestId) {
    return response.getExtensionsByUrl(DocumentationMapper.QR_CONTEXT).stream()
        .map(Extension::getValue)
        .anyMatch(
            value ->
                value instanceof Reference reference
                    && "ServiceRequest".equals(reference.getReferenceElement().getResourceType())
                    && serviceRequestId.equals(reference.getReferenceElement().getIdPart()));
  }

  private static String requesterId(ServiceRequest order) {
    Reference requester = order.getRequester();
    return "Practitioner".equals(requester.getReferenceElement().getResourceType())
        ? requester.getReferenceElement().getIdPart()
        : null;
  }

  private static Extension origin(
      List<QuestionnaireResponseItemAnswerComponent> previous,
      Type value,
      String author,
      String linkId) {
    for (QuestionnaireResponseItemAnswerComponent answer : previous) {
      Extension origin = answer.getExtensionByUrl(DocumentationMapper.INFORMATION_ORIGIN);
      if (origin != null && sameValue(answer.getValue(), value)) {
        return origin.copy();
      }
    }
    if (author == null) {
      throw new DocumentationValidationException(
          "No practitioner to record as the author of the answer to '" + linkId + "'");
    }
    boolean wasPrefilled =
        previous.stream()
            .map(DocumentationMapper::origin)
            .anyMatch(source -> source != null && !MANUAL.equals(source));
    return DocumentationMapper.origin(wasPrefilled ? OVERRIDE : MANUAL, author);
  }

  private static void collectQuestions(
      List<QuestionnaireItemComponent> items, Map<String, QuestionnaireItemComponent> questions) {
    for (QuestionnaireItemComponent item : items) {
      if (item.getType() != QuestionnaireItemType.GROUP
          && item.getType() != QuestionnaireItemType.DISPLAY) {
        questions.put(item.getLinkId(), item);
      }
      collectQuestions(item.getItem(), questions);
    }
  }

  /** Removes the answers of disabled items, in form order, and collects the enabled linkIds. */
  private static void dropDisabled(
      List<QuestionnaireItemComponent> items,
      Map<String, List<QuestionnaireResponseItemAnswerComponent>> answers,
      Set<String> enabled,
      boolean parentEnabled) {
    for (QuestionnaireItemComponent item : items) {
      boolean itemEnabled = parentEnabled && isEnabled(item, answers);
      if (itemEnabled) {
        enabled.add(item.getLinkId());
      } else {
        answers.remove(item.getLinkId());
      }
      dropDisabled(item.getItem(), answers, enabled, itemEnabled);
    }
  }

  private static boolean isEnabled(
      QuestionnaireItemComponent item,
      Map<String, List<QuestionnaireResponseItemAnswerComponent>> answers) {
    if (!item.hasEnableWhen()) {
      return true;
    }
    boolean any = item.getEnableBehavior() == Questionnaire.EnableWhenBehavior.ANY;
    for (QuestionnaireItemEnableWhenComponent condition : item.getEnableWhen()) {
      boolean met = isMet(condition, answers.getOrDefault(condition.getQuestion(), List.of()));
      if (any && met) {
        return true;
      }
      if (!any && !met) {
        return false;
      }
    }
    return !any;
  }

  private static boolean isMet(
      QuestionnaireItemEnableWhenComponent condition,
      List<QuestionnaireResponseItemAnswerComponent> answers) {
    Type expected = condition.getAnswer();
    if (condition.getOperator() == Questionnaire.QuestionnaireItemOperator.EXISTS) {
      return expected instanceof PrimitiveType<?> flag
          && Boolean.valueOf(flag.getValueAsString()) == !answers.isEmpty();
    }
    return answers.stream()
        .map(QuestionnaireResponseItemAnswerComponent::getValue)
        .anyMatch(
            actual ->
                switch (condition.getOperator()) {
                  case EQUAL -> sameValue(actual, expected);
                  case NOT_EQUAL -> !sameValue(actual, expected);
                  case GREATER_THAN -> compare(actual, expected) > 0;
                  case LESS_THAN -> compare(actual, expected) < 0;
                  case GREATER_OR_EQUAL -> compare(actual, expected) >= 0;
                  case LESS_OR_EQUAL -> compare(actual, expected) <= 0;
                  case null, default -> false;
                });
  }

  static boolean sameValue(Type a, Type b) {
    if (a instanceof Coding x && b instanceof Coding y) {
      return Objects.equals(x.getSystem(), y.getSystem())
          && Objects.equals(x.getCode(), y.getCode());
    }
    if (number(a) != null && number(b) != null) {
      return number(a).compareTo(number(b)) == 0;
    }
    return a instanceof PrimitiveType<?> x
        && b instanceof PrimitiveType<?> y
        && Objects.equals(x.getValueAsString(), y.getValueAsString());
  }

  /** Compares numbers by value and other primitives (ISO dates) as text; 0 when not comparable. */
  private static int compare(Type a, Type b) {
    if (number(a) != null && number(b) != null) {
      return number(a).compareTo(number(b));
    }
    if (a instanceof PrimitiveType<?> x
        && b instanceof PrimitiveType<?> y
        && x.hasValue()
        && y.hasValue()) {
      return x.getValueAsString().compareTo(y.getValueAsString());
    }
    return 0;
  }

  private static BigDecimal number(Type value) {
    return switch (value) {
      case IntegerType i when i.hasValue() -> BigDecimal.valueOf(i.getValue());
      case DecimalType d when d.hasValue() -> d.getValue();
      case null, default -> null;
    };
  }

  private static void collectMissing(
      List<QuestionnaireItemComponent> items,
      Map<String, List<QuestionnaireResponseItemAnswerComponent>> answers,
      Set<String> enabled,
      List<String> missing) {
    for (QuestionnaireItemComponent item : items) {
      if (!enabled.contains(item.getLinkId()) || item.getType() == QuestionnaireItemType.DISPLAY) {
        continue;
      }
      if (item.getType() == QuestionnaireItemType.GROUP) {
        collectMissing(item.getItem(), answers, enabled, missing);
      } else if (item.getRequired()
          && answers.getOrDefault(item.getLinkId(), List.of()).isEmpty()) {
        missing.add(item.getLinkId());
      }
    }
  }

  private static List<QuestionnaireResponseItemComponent> responseItems(
      List<QuestionnaireItemComponent> items,
      Map<String, List<QuestionnaireResponseItemAnswerComponent>> answers) {
    List<QuestionnaireResponseItemComponent> result = new ArrayList<>();
    for (QuestionnaireItemComponent item : items) {
      QuestionnaireResponseItemComponent responseItem =
          new QuestionnaireResponseItemComponent()
              .setLinkId(item.getLinkId())
              .setText(item.getText());
      if (item.getType() == QuestionnaireItemType.GROUP) {
        responseItem.setItem(responseItems(item.getItem(), answers));
        if (responseItem.hasItem()) {
          result.add(responseItem);
        }
      } else if (item.getType() != QuestionnaireItemType.DISPLAY
          && !answers.getOrDefault(item.getLinkId(), List.of()).isEmpty()) {
        answers.get(item.getLinkId()).forEach(answer -> responseItem.addAnswer(answer.copy()));
        result.add(responseItem);
      }
    }
    return result;
  }

  private record Form(
      ServiceRequest order,
      String patientId,
      Coverage coverage,
      DtrPackage questionnairePackage,
      QuestionnaireResponse saved) {}
}
