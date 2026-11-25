package io.github.samarthshubham.clinic.client;

import java.util.List;
import org.hl7.fhir.r4.model.Questionnaire;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.ValueSet;

public record DtrPackage(
    Questionnaire questionnaire, QuestionnaireResponse response, List<ValueSet> valueSets) {

  public DtrPackage {
    valueSets = List.copyOf(valueSets);
  }
}
