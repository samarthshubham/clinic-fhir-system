package io.github.samarthshubham.payer.interop;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import io.github.samarthshubham.payer.service.QuestionnairePackageService;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

@Component
public class QuestionnairePackageProvider implements IResourceProvider {

  public static final String OPERATION = "$questionnaire-package";

  private final QuestionnairePackageService questionnairePackages;

  public QuestionnairePackageProvider(QuestionnairePackageService questionnairePackages) {
    this.questionnairePackages = questionnairePackages;
  }

  @Override
  public Class<? extends IBaseResource> getResourceType() {
    return Questionnaire.class;
  }

  @Operation(
      name = OPERATION,
      idempotent = false,
      canonicalUrl = "http://hl7.org/fhir/us/davinci-dtr/OperationDefinition/questionnaire-package")
  public Parameters questionnairePackage(
      @OperationParam(name = "coverage", min = 1, max = 1) Coverage coverage,
      @OperationParam(name = "order") List<IBaseResource> orders,
      @OperationParam(name = "questionnaire") List<CanonicalType> questionnaires,
      @OperationParam(name = "context", max = 1) StringType context,
      @OperationParam(name = "changedsince", max = 1) InstantType changedSince) {
    if (coverage == null) {
      throw new InvalidRequestException("The coverage parameter is required");
    }
    if (!coverage.getIdElement().hasIdPart() || !coverage.getBeneficiary().hasReference()) {
      throw new InvalidRequestException("The coverage needs an id and a beneficiary");
    }
    List<Resource> orderResources = orders == null ? List.of() : orderResources(orders);
    List<String> canonicals =
        questionnaires == null
            ? List.of()
            : questionnaires.stream()
                .map(CanonicalType::getValue)
                .filter(Objects::nonNull)
                .toList();
    if (orderResources.isEmpty() && canonicals.isEmpty() && context == null) {
      throw new InvalidRequestException("Send at least one order, questionnaire or context");
    }
    return questionnairePackages.questionnairePackages(
        coverage,
        orderResources,
        canonicals,
        changedSince == null ? null : changedSince.getValue());
  }

  private static List<Resource> orderResources(List<IBaseResource> orders) {
    return orders.stream().map(Resource.class::cast).toList();
  }
}
