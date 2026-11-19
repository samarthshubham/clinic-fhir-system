package io.github.samarthshubham.clinic.service.dtr;

import ca.uhn.fhir.context.FhirContext;
import io.github.samarthshubham.clinic.client.DtrPackage;
import io.github.samarthshubham.clinic.support.GoldenFhir;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;

public final class DtrFixtures {

  public static final String MRI_CANONICAL =
      "http://praxishealthplan.example/fhir/Questionnaire/mri-lumbar-spine";

  private DtrFixtures() {}

  public static <T extends Resource> T parse(Class<T> type, String fixture) {
    return FhirContext.forR4Cached().newJsonParser().parseResource(type, GoldenFhir.read(fixture));
  }

  /** The MRI package as payer-api sends it: the form, an empty response and its ValueSet. */
  public static DtrPackage mriPackage() {
    QuestionnaireResponse empty = new QuestionnaireResponse();
    empty.setQuestionnaire(MRI_CANONICAL);
    empty.setStatus(QuestionnaireResponse.QuestionnaireResponseStatus.INPROGRESS);
    empty.addExtension(
        "http://hl7.org/fhir/us/davinci-dtr/StructureDefinition/intendedUse",
        new CodeableConcept(
            new Coding(
                "http://hl7.org/fhir/us/davinci-crd/CodeSystem/coverage-information-codes",
                "withpa",
                "Include in prior authorization")));
    return new DtrPackage(
        parse(Questionnaire.class, "dtr/questionnaire-mri-lumbar-spine.json"),
        empty,
        List.of(parse(ValueSet.class, "dtr/valueset-lumbar-spine-indications.json")));
  }

  public static QuestionnaireResponse mriPrefilled() {
    return parse(QuestionnaireResponse.class, "dtr/qr-mri-prefilled.json");
  }
}
