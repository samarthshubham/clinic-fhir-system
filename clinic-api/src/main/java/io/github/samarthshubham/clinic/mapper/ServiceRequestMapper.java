package io.github.samarthshubham.clinic.mapper;

import io.github.samarthshubham.clinic.client.CrdClient;
import io.github.samarthshubham.clinic.dto.CoverageInformation;
import io.github.samarthshubham.clinic.dto.CrdCard;
import io.github.samarthshubham.clinic.dto.ServiceRequestRequest;
import io.github.samarthshubham.clinic.dto.ServiceRequestResponse;
import java.util.List;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.springframework.stereotype.Component;

@Component
public class ServiceRequestMapper {

  static final String CRD_CARD = "http://praxis.clinic/fhir/extension/crd-card";

  public ServiceRequest toFhir(ServiceRequestRequest request) {
    ServiceRequest sr = new ServiceRequest();
    sr.getMeta().addProfile(UsCoreProfiles.SERVICE_REQUEST);
    sr.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);
    sr.setSubject(new Reference("Patient/" + request.patientId()));
    sr.setStatus(ServiceRequest.ServiceRequestStatus.fromCode(request.status()));
    sr.setPriority(ServiceRequest.ServiceRequestPriority.fromCode(request.priority()));
    CodeableConcept code = new CodeableConcept().setText(request.display());
    if (request.code() != null) {
      code.addCoding(new Coding().setSystem(request.codeSystem()).setCode(request.code()));
    }
    sr.setCode(code);
    sr.addCategory(new CodeableConcept().setText(request.category()));
    if (request.encounterId() != null) {
      sr.setEncounter(new Reference("Encounter/" + request.encounterId()));
    }
    if (request.practitionerId() != null) {
      sr.setRequester(new Reference("Practitioner/" + request.practitionerId()));
    }
    sr.setAuthoredOnElement(new DateTimeType(request.authoredOn().toString()));
    return sr;
  }

  public ServiceRequestResponse toResponse(ServiceRequest serviceRequest) {
    return new ServiceRequestResponse(
        FhirValues.idOf(serviceRequest),
        FhirValues.referenceId(serviceRequest.getSubject()),
        FhirValues.referenceId(serviceRequest.getEncounter()),
        FhirValues.referenceId(serviceRequest.getRequester()),
        serviceRequest.getCode().getText(),
        serviceRequest.getCode().hasCoding()
            ? serviceRequest.getCode().getCodingFirstRep().getSystem()
            : null,
        serviceRequest.getCode().hasCoding()
            ? serviceRequest.getCode().getCodingFirstRep().getCode()
            : null,
        serviceRequest.hasCategory() ? serviceRequest.getCategoryFirstRep().getText() : null,
        serviceRequest.hasStatus() ? serviceRequest.getStatus().toCode() : null,
        serviceRequest.hasPriority() ? serviceRequest.getPriority().toCode() : null,
        FhirValues.localDate(serviceRequest.getAuthoredOnElement()),
        coverageInformation(serviceRequest),
        cards(serviceRequest));
  }

  public void addCrdResult(
      ServiceRequest serviceRequest, List<Extension> coverageInformation, List<CrdCard> cards) {
    serviceRequest.getExtension().addAll(coverageInformation);
    for (CrdCard card : cards) {
      Extension extension = serviceRequest.addExtension().setUrl(CRD_CARD);
      addString(extension, "summary", card.summary());
      addString(extension, "indicator", card.indicator());
      addString(extension, "detail", card.detail());
      addString(extension, "source", card.source());
    }
  }

  private static CoverageInformation coverageInformation(ServiceRequest serviceRequest) {
    Extension extension = serviceRequest.getExtensionByUrl(CrdClient.COVERAGE_INFORMATION);
    if (extension == null) {
      return null;
    }
    return new CoverageInformation(
        value(extension, "coverage") instanceof Reference coverage
            ? FhirValues.referenceId(coverage)
            : null,
        primitive(extension, "covered"),
        primitive(extension, "pa-needed"),
        primitive(extension, "doc-needed"),
        value(extension, "reason") instanceof CodeableConcept reason ? reason.getText() : null,
        primitive(extension, "questionnaire"),
        value(extension, "date") instanceof BaseDateTimeType date
            ? FhirValues.localDate(date)
            : null,
        primitive(extension, "coverage-assertion-id"));
  }

  private static List<CrdCard> cards(ServiceRequest serviceRequest) {
    return serviceRequest.getExtensionsByUrl(CRD_CARD).stream()
        .map(
            card ->
                new CrdCard(
                    primitive(card, "summary"),
                    primitive(card, "indicator"),
                    primitive(card, "detail"),
                    primitive(card, "source")))
        .toList();
  }

  private static void addString(Extension extension, String url, String value) {
    if (value != null) {
      extension.addExtension(url, new StringType(value));
    }
  }

  private static String primitive(Extension extension, String url) {
    return value(extension, url) instanceof PrimitiveType<?> value
        ? value.getValueAsString()
        : null;
  }

  private static Type value(Extension extension, String url) {
    Extension child = extension.getExtensionByUrl(url);
    return child == null ? null : child.getValue();
  }
}
