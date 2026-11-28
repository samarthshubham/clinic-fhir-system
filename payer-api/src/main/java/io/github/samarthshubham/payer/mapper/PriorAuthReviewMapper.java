package io.github.samarthshubham.payer.mapper;

import io.github.samarthshubham.payer.dto.PriorAuthReviewResponse;
import io.github.samarthshubham.payer.dto.PriorAuthReviewResponse.Answer;
import io.github.samarthshubham.payer.dto.PriorAuthReviewResponse.ClaimLine;
import io.github.samarthshubham.payer.dto.PriorAuthReviewResponse.Criterion;
import io.github.samarthshubham.payer.dto.PriorAuthReviewResponse.Rule;
import io.github.samarthshubham.payer.service.CoverageRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.QuestionnaireResponse.QuestionnaireResponseItemComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Type;
import org.springframework.stereotype.Component;

@Component
public class PriorAuthReviewMapper {

  public PriorAuthReviewResponse toResponse(
      Claim claim,
      ClaimResponse response,
      boolean pended,
      List<QuestionnaireResponse> questionnaires,
      List<DocumentReference> documents,
      List<CoverageRule> rules,
      Map<String, String> ruleAnswers) {
    List<Answer> answers = new ArrayList<>();
    questionnaires.forEach(questionnaire -> collectAnswers(questionnaire.getItem(), answers));

    List<ClaimLine> lines =
        IntStream.range(0, claim.getItem().size())
            .mapToObj(index -> line(claim.getItem().get(index), rules.get(index), ruleAnswers))
            .toList();
    Stream<PriorAuthReviewResponse.Attachment> documentAttachments =
        documents.stream()
            .flatMap(
                document ->
                    document.getContent().stream()
                        .map(
                            content ->
                                attachment(content.getAttachment(), document.getDescription())));
    Stream<PriorAuthReviewResponse.Attachment> inlineAttachments =
        claim.getSupportingInfo().stream()
            .map(Claim.SupportingInformationComponent::getValue)
            .filter(Attachment.class::isInstance)
            .map(Attachment.class::cast)
            .map(value -> attachment(value, null));
    return new PriorAuthReviewResponse(
        claim.getIdPart(),
        response.getPreAuthRef(),
        response.getDisposition(),
        pended,
        lines,
        List.copyOf(answers),
        Stream.concat(documentAttachments, inlineAttachments).toList());
  }

  private static ClaimLine line(
      Claim.ItemComponent item, CoverageRule rule, Map<String, String> answers) {
    CodeableConcept service = item.getProductOrService();
    Coding coding = service.getCodingFirstRep();
    String serviceName = service.hasText() ? service.getText() : coding.getDisplay();
    return new ClaimLine(
        item.getSequence(),
        serviceName,
        coding.getCode(),
        rule.display() == null
            ? null
            : new Rule(
                rule.display(),
                rule.paNeeded(),
                rule.autoApproveCriteria().stream()
                    .map(
                        criterion ->
                            new Criterion(
                                criterion.linkId(),
                                criterion.operator().name(),
                                criterion.value(),
                                criterion.isMetBy(answers.get(criterion.linkId()))))
                    .toList()));
  }

  private static void collectAnswers(
      List<QuestionnaireResponseItemComponent> items, List<Answer> answers) {
    for (QuestionnaireResponseItemComponent item : items) {
      List<String> values =
          item.getAnswer().stream()
              .map(answer -> text(answer.getValue()))
              .filter(Objects::nonNull)
              .toList();
      if (!values.isEmpty()) {
        answers.add(new Answer(item.getLinkId(), item.getText(), values));
      }
      collectAnswers(item.getItem(), answers);
      item.getAnswer().forEach(answer -> collectAnswers(answer.getItem(), answers));
    }
  }

  private static String text(Type value) {
    return switch (value) {
      case Coding coding -> coding.hasDisplay() ? coding.getDisplay() : coding.getCode();
      case Quantity quantity ->
          quantity.hasValue()
              ? quantity.getValue().toPlainString()
                  + (quantity.hasUnit() ? " " + quantity.getUnit() : "")
              : null;
      case Reference reference ->
          reference.hasDisplay() ? reference.getDisplay() : reference.getReference();
      case Attachment attachment -> attachment.hasTitle() ? attachment.getTitle() : "Attachment";
      case null -> null;
      default -> value.isPrimitive() ? value.primitiveValue() : null;
    };
  }

  private static PriorAuthReviewResponse.Attachment attachment(
      Attachment attachment, String description) {
    return new PriorAuthReviewResponse.Attachment(
        attachment.hasTitle() ? attachment.getTitle() : description,
        attachment.getContentType(),
        attachment.hasSize() ? attachment.getSize() : null);
  }
}
