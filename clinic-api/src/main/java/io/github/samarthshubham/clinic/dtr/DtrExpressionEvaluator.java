package io.github.samarthshubham.clinic.dtr;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.fhirpath.FHIRPathEngine;
import org.hl7.fhir.r4.fhirpath.FHIRPathUtilityClasses.FunctionDetails;
import org.hl7.fhir.r4.fhirpath.TypeDetails;
import org.hl7.fhir.r4.hapi.ctx.HapiWorkerContext;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Expression;
import org.hl7.fhir.r4.model.ValueSet;
import org.springframework.stereotype.Component;

@Component
public class DtrExpressionEvaluator {

  static final String FHIRPATH = "text/fhirpath";
  static final String FHIR_QUERY = "application/x-fhir-query";

  private static final Pattern EMBEDDED_FHIRPATH = Pattern.compile("\\{\\{(.+?)}}");

  private final FHIRPathEngine engine;
  private final IGenericClient fhirClient;

  public DtrExpressionEvaluator(FhirContext fhirContext, IGenericClient fhirClient) {
    this.engine =
        new FHIRPathEngine(new HapiWorkerContext(fhirContext, fhirContext.getValidationSupport()));
    this.engine.setHostServices(new VariableScope());
    this.fhirClient = fhirClient;
  }

  public List<Base> evaluate(Expression expression, Map<String, List<Base>> variables) {
    return switch (expression.getLanguage()) {
      case FHIRPATH -> fhirPath(expression.getExpression(), variables);
      case FHIR_QUERY -> List.of(fhirQuery(expression.getExpression(), variables));
      default ->
          throw new UnsupportedExpressionException(
              "Unsupported expression language " + expression.getLanguage());
    };
  }

  private List<Base> fhirPath(String expression, Map<String, List<Base>> variables) {
    Base focus = variables.getOrDefault("patient", List.of()).stream().findFirst().orElse(null);
    return engine.evaluate(variables, focus, focus, focus, engine.parse(expression));
  }

  private Bundle fhirQuery(String query, Map<String, List<Base>> variables) {
    Matcher embedded = EMBEDDED_FHIRPATH.matcher(query);
    StringBuilder url = new StringBuilder();
    while (embedded.find()) {
      String value = engine.convertToString(fhirPath(embedded.group(1), variables));
      embedded.appendReplacement(
          url, Matcher.quoteReplacement(URLEncoder.encode(value, StandardCharsets.UTF_8)));
    }
    embedded.appendTail(url);
    return fhirClient.search().byUrl(url.toString()).returnBundle(Bundle.class).execute();
  }

  static class UnsupportedExpressionException extends RuntimeException {
    UnsupportedExpressionException(String message) {
      super(message);
    }
  }

  private static final class VariableScope implements FHIRPathEngine.IEvaluationContext {

    @Override
    @SuppressWarnings("unchecked")
    public List<Base> resolveConstant(
        FHIRPathEngine engine,
        Object appContext,
        String name,
        boolean beforeContext,
        boolean explicitConstant) {
      if (!explicitConstant) {
        return List.of();
      }
      List<Base> value = ((Map<String, List<Base>>) appContext).get(name);
      if (value == null) {
        throw new UnsupportedExpressionException("Unknown variable %" + name);
      }
      return value;
    }

    @Override
    public TypeDetails resolveConstantType(
        FHIRPathEngine engine, Object appContext, String name, boolean explicitConstant) {
      return null;
    }

    @Override
    public boolean log(String argument, List<Base> focus) {
      return false;
    }

    @Override
    public FunctionDetails resolveFunction(FHIRPathEngine engine, String functionName) {
      return null;
    }

    @Override
    public TypeDetails checkFunction(
        FHIRPathEngine engine,
        Object appContext,
        String functionName,
        TypeDetails focus,
        List<TypeDetails> parameters) {
      return null;
    }

    @Override
    public List<Base> executeFunction(
        FHIRPathEngine engine,
        Object appContext,
        List<Base> focus,
        String functionName,
        List<List<Base>> parameters) {
      return null;
    }

    @Override
    public Base resolveReference(
        FHIRPathEngine engine, Object appContext, String url, Base refContext) {
      return null;
    }

    @Override
    public boolean conformsToProfile(
        FHIRPathEngine engine, Object appContext, Base item, String url) {
      return false;
    }

    @Override
    public ValueSet resolveValueSet(FHIRPathEngine engine, Object appContext, String url) {
      return null;
    }
  }
}
