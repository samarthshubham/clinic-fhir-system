package io.github.samarthshubham.payer.service;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.UriClientParam;
import io.github.samarthshubham.payer.mapper.PageMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DeviceRequest;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Service;

@Service
public class QuestionnairePackageService {

  public static final String PAYER_BASE = "http://praxishealthplan.example/fhir";
  static final String PACKAGE_BUNDLE_PROFILE =
      "http://hl7.org/fhir/us/davinci-dtr/StructureDefinition/DTR-QPackageBundle";
  static final String OUTPUT_PROFILE =
      "http://hl7.org/fhir/us/davinci-dtr/StructureDefinition/dtr-qpackage-output-parameters";
  static final String RESPONSE_PROFILE =
      "http://hl7.org/fhir/us/davinci-dtr/StructureDefinition/dtr-questionnaireresponse";
  static final String QR_COVERAGE =
      "http://hl7.org/fhir/us/davinci-dtr/StructureDefinition/qr-coverage";
  static final String QR_CONTEXT =
      "http://hl7.org/fhir/us/davinci-dtr/StructureDefinition/qr-context";
  static final String INTENDED_USE =
      "http://hl7.org/fhir/us/davinci-dtr/StructureDefinition/intendedUse";
  static final String COVERAGE_INFORMATION_CODES =
      "http://hl7.org/fhir/us/davinci-crd/CodeSystem/coverage-information-codes";
  static final String CQF_LIBRARY = "http://hl7.org/fhir/StructureDefinition/cqf-library";
  static final int MAX_RESULTS = 100;

  private final IGenericClient fhirClient;
  private final CoverageRuleService coverageRules;
  private final Clock clock;

  public QuestionnairePackageService(
      IGenericClient fhirClient, CoverageRuleService coverageRules, Clock clock) {
    this.fhirClient = fhirClient;
    this.coverageRules = coverageRules;
    this.clock = clock;
  }

  public Parameters questionnairePackages(
      Coverage coverage, List<Resource> orders, List<String> questionnaires, Date changedSince) {
    Map<String, List<Resource>> ordersByCanonical = new LinkedHashMap<>();
    questionnaires.forEach(canonical -> ordersByCanonical.put(canonical, new ArrayList<>()));
    for (Resource order : orders) {
      String canonical = questionnaireFor(order);
      if (canonical != null) {
        ordersByCanonical.computeIfAbsent(canonical, k -> new ArrayList<>()).add(order);
      }
    }

    Parameters output = new Parameters();
    output.getMeta().addProfile(OUTPUT_PROFILE);
    OperationOutcome outcome = new OperationOutcome();
    if (ordersByCanonical.isEmpty()) {
      addIssue(
          outcome,
          OperationOutcome.IssueSeverity.INFORMATION,
          OperationOutcome.IssueType.INFORMATIONAL,
          "Praxis Health Plan needs no documentation for these orders");
    }

    List<Questionnaire> found =
        search(Questionnaire.class, Questionnaire.URL, urls(ordersByCanonical.keySet()));
    Map<Questionnaire, List<Resource>> packages = new LinkedHashMap<>();
    ordersByCanonical.forEach(
        (canonical, canonicalOrders) -> {
          Questionnaire questionnaire = matching(found, canonical);
          if (questionnaire == null) {
            addIssue(
                outcome,
                OperationOutcome.IssueSeverity.WARNING,
                OperationOutcome.IssueType.NOTFOUND,
                "Praxis Health Plan has no Questionnaire " + canonical);
          } else {
            packages.computeIfAbsent(questionnaire, k -> new ArrayList<>()).addAll(canonicalOrders);
          }
        });

    List<Library> libraries =
        search(
            Library.class,
            Library.URL,
            packages.keySet().stream().flatMap(QuestionnairePackageService::libraryUrls).toList());
    List<ValueSet> valueSets =
        search(
            ValueSet.class,
            ValueSet.URL,
            packages.keySet().stream()
                .flatMap(q -> valueSetUrls(q.getItem()))
                .map(QuestionnairePackageService::withoutVersion)
                .toList());

    packages.forEach(
        (questionnaire, packageOrders) -> {
          List<Library> packageLibraries =
              resolve(libraries, libraryUrls(questionnaire).toList(), outcome);
          List<ValueSet> packageValueSets =
              resolve(valueSets, valueSetUrls(questionnaire.getItem()).toList(), outcome);
          if (changedSince == null
              || changedSince(changedSince, questionnaire, packageLibraries, packageValueSets)) {
            output
                .addParameter()
                .setName("packagebundle")
                .setResource(
                    packageBundle(
                        questionnaire,
                        coverage,
                        packageOrders,
                        packageLibraries,
                        packageValueSets));
          }
        });

    if (outcome.hasIssue()) {
      output.addParameter().setName("outcome").setResource(outcome);
    }
    return output;
  }

  private String questionnaireFor(Resource order) {
    for (Coding coding : codings(order)) {
      if (coding.hasSystem() && coding.hasCode()) {
        CoverageRule rule = coverageRules.findRule(coding.getSystem(), coding.getCode());
        if (rule.docNeeded()) {
          return rule.questionnaire();
        }
      }
    }
    return null;
  }

  private static List<Coding> codings(Resource order) {
    CodeableConcept code =
        switch (order) {
          case ServiceRequest request -> request.getCode();
          case DeviceRequest request ->
              request.hasCodeCodeableConcept() ? request.getCodeCodeableConcept() : null;
          case MedicationRequest request ->
              request.hasMedicationCodeableConcept()
                  ? request.getMedicationCodeableConcept()
                  : null;
          default -> null;
        };
    return code == null ? List.of() : code.getCoding();
  }

  private <T extends MetadataResource> List<T> search(
      Class<T> type, UriClientParam url, Collection<String> urls) {
    List<String> distinct = urls.stream().distinct().toList();
    if (distinct.isEmpty()) {
      return List.of();
    }
    Bundle bundle =
        fhirClient
            .search()
            .forResource(type)
            .where(url.matches().values(distinct))
            .count(MAX_RESULTS)
            .returnBundle(Bundle.class)
            .execute();
    return PageMapper.resources(bundle, type);
  }

  private static <T extends MetadataResource> T matching(List<T> resources, String canonical) {
    String url = withoutVersion(canonical);
    String version =
        canonical.contains("|") ? canonical.substring(canonical.indexOf('|') + 1) : null;
    return resources.stream()
        .filter(resource -> url.equals(resource.getUrl()))
        .filter(resource -> version == null || version.equals(resource.getVersion()))
        .findFirst()
        .orElse(null);
  }

  private static <T extends MetadataResource> List<T> resolve(
      List<T> resources, List<String> canonicals, OperationOutcome outcome) {
    List<T> resolved = new ArrayList<>();
    for (String canonical : new LinkedHashSet<>(canonicals)) {
      T resource = matching(resources, canonical);
      if (resource == null) {
        addIssue(
            outcome,
            OperationOutcome.IssueSeverity.WARNING,
            OperationOutcome.IssueType.NOTFOUND,
            "Praxis Health Plan has no " + canonical + " for the package");
      } else if (!resolved.contains(resource)) {
        resolved.add(resource);
      }
    }
    return resolved;
  }

  private static Stream<String> libraryUrls(Questionnaire questionnaire) {
    return questionnaire.getExtensionsByUrl(CQF_LIBRARY).stream()
        .map(extension -> ((CanonicalType) extension.getValue()).getValue())
        .filter(Objects::nonNull);
  }

  private static Stream<String> valueSetUrls(List<Questionnaire.QuestionnaireItemComponent> items) {
    return items.stream()
        .flatMap(
            item ->
                Stream.concat(
                    item.hasAnswerValueSet() && !item.getAnswerValueSet().startsWith("#")
                        ? Stream.of(item.getAnswerValueSet())
                        : Stream.empty(),
                    valueSetUrls(item.getItem())));
  }

  private static List<String> urls(Set<String> canonicals) {
    return canonicals.stream().map(QuestionnairePackageService::withoutVersion).toList();
  }

  private static String withoutVersion(String canonical) {
    int bar = canonical.indexOf('|');
    return bar < 0 ? canonical : canonical.substring(0, bar);
  }

  private static boolean changedSince(
      Date since, Questionnaire questionnaire, List<Library> libraries, List<ValueSet> valueSets) {
    return Stream.of(List.of(questionnaire), libraries, valueSets)
        .flatMap(List::stream)
        .map(resource -> resource.getMeta().getLastUpdated())
        .anyMatch(lastUpdated -> lastUpdated == null || lastUpdated.after(since));
  }

  private Bundle packageBundle(
      Questionnaire questionnaire,
      Coverage coverage,
      List<Resource> orders,
      List<Library> libraries,
      List<ValueSet> valueSets) {
    Bundle bundle = new Bundle().setType(Bundle.BundleType.COLLECTION);
    bundle.getMeta().addProfile(PACKAGE_BUNDLE_PROFILE);
    addEntry(bundle, questionnaire);
    QuestionnaireResponse response = emptyResponse(questionnaire, coverage, orders);
    bundle
        .addEntry()
        .setFullUrl("urn:uuid:" + response.getIdElement().getIdPart())
        .setResource(response);
    libraries.forEach(library -> addEntry(bundle, library));
    valueSets.forEach(valueSet -> addEntry(bundle, valueSet));
    return bundle;
  }

  private static void addEntry(Bundle bundle, MetadataResource resource) {
    MetadataResource copy = resource.copy();
    copy.setId(resource.getIdElement().getIdPart());
    copy.getMeta().setVersionId(null).setLastUpdated(null).setSource(null);
    bundle
        .addEntry()
        .setFullUrl(PAYER_BASE + "/" + copy.fhirType() + "/" + copy.getIdPart())
        .setResource(copy);
  }

  private QuestionnaireResponse emptyResponse(
      Questionnaire questionnaire, Coverage coverage, List<Resource> orders) {
    QuestionnaireResponse response = new QuestionnaireResponse();
    List<String> orderReferences =
        orders.stream()
            .filter(Resource::hasIdElement)
            .map(order -> order.fhirType() + "/" + order.getIdElement().getIdPart())
            .toList();
    String coverageReference = "Coverage/" + coverage.getIdElement().getIdPart();
    String packageKey =
        String.join(
            "|", questionnaire.getUrl(), coverageReference, String.join(",", orderReferences));
    response.setId(UUID.nameUUIDFromBytes(packageKey.getBytes(StandardCharsets.UTF_8)).toString());
    response.getMeta().addProfile(RESPONSE_PROFILE);
    response.addExtension(QR_COVERAGE, new Reference(coverageReference));
    response.addExtension(
        INTENDED_USE,
        new CodeableConcept(
            new Coding(COVERAGE_INFORMATION_CODES, "withpa", "Include in prior authorization")));
    orderReferences.forEach(
        reference -> response.addExtension(QR_CONTEXT, new Reference(reference)));
    response.setQuestionnaire(
        questionnaire.hasVersion()
            ? questionnaire.getUrl() + "|" + questionnaire.getVersion()
            : questionnaire.getUrl());
    response.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS);
    response.setAuthoredElement(
        new DateTimeType(
            Date.from(clock.instant()), TemporalPrecisionEnum.SECOND, TimeZone.getTimeZone("UTC")));
    response.setSubject(new Reference(coverage.getBeneficiary().getReference()));
    return response;
  }

  private static void addIssue(
      OperationOutcome outcome,
      OperationOutcome.IssueSeverity severity,
      OperationOutcome.IssueType type,
      String diagnostics) {
    outcome.addIssue().setSeverity(severity).setCode(type).setDiagnostics(diagnostics);
  }
}
