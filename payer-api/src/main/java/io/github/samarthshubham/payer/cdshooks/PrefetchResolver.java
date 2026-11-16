package io.github.samarthshubham.payer.cdshooks;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.samarthshubham.payer.config.CrdProperties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PrefetchResolver {

  private static final Logger log = LoggerFactory.getLogger(PrefetchResolver.class);
  private static final Pattern CONTEXT_TOKEN = Pattern.compile("\\{\\{context\\.(\\w+)}}");
  private static final Pattern ID_OR_REFERENCE =
      Pattern.compile("^([A-Z][A-Za-z]+/)?[A-Za-z0-9\\-.]{1,64}$");

  private final FhirContext fhirContext;
  private final CrdProperties crdProperties;

  public PrefetchResolver(FhirContext fhirContext, CrdProperties crdProperties) {
    this.fhirContext = fhirContext;
    this.crdProperties = crdProperties;
  }

  /**
   * Returns the prefetched resource for {@code key}, or fetches it from the hook's {@code
   * fhirServer} when the client left it out. Only servers on {@code
   * payer.crd.prefetch-fhir-servers} are queried, so a hook request cannot make payer-api call an
   * arbitrary URL.
   */
  IBaseResource resolve(CdsHookRequest request, String key, IParser parser) {
    String template = CrdHook.PREFETCH.get(key);
    if (template == null) {
      throw new IllegalArgumentException("No prefetch template named " + key);
    }
    JsonNode prefetched = request.prefetched(key);
    if (prefetched != null) {
      try {
        return parser.parseResource(prefetched.toString());
      } catch (DataFormatException e) {
        throw CdsHookRequest.badRequest("Prefetch '" + key + "' is not a FHIR resource");
      }
    }
    if (!crdProperties.mayFetchPrefetchFrom(request.fhirServer())) {
      throw new CdsHookException(
          HttpStatus.PRECONDITION_FAILED,
          "Prefetch '" + key + "' is missing and payer-api may not query the hook's fhirServer");
    }
    return fetch(request, key, render(template, request));
  }

  private IBaseResource fetch(CdsHookRequest request, String key, String query) {
    IGenericClient client = fhirContext.newRestfulGenericClient(request.fhirServer());
    if (request.accessToken() != null) {
      client.registerInterceptor(new BearerTokenAuthInterceptor(request.accessToken()));
    }
    try {
      return client.fetchResourceFromUrl(resultType(query), client.getServerBase() + "/" + query);
    } catch (BaseServerResponseException e) {
      log.warn("Prefetch fallback for '{}' failed: {}", key, e.getMessage());
      throw new CdsHookException(
          HttpStatus.PRECONDITION_FAILED,
          "Prefetch '" + key + "' is missing and the hook's fhirServer did not return it",
          e);
    }
  }

  private Class<? extends IBaseResource> resultType(String query) {
    if (query.contains("?")) {
      return Bundle.class;
    }
    String resourceType = query.substring(0, Math.max(query.indexOf('/'), 0));
    try {
      return fhirContext.getResourceDefinition(resourceType).getImplementingClass();
    } catch (DataFormatException e) {
      throw CdsHookRequest.badRequest("The prefetch query " + query + " is not a FHIR read");
    }
  }

  static String render(String template, CdsHookRequest request) {
    Matcher matcher = CONTEXT_TOKEN.matcher(template);
    StringBuilder rendered = new StringBuilder();
    while (matcher.find()) {
      String value = request.contextValue(matcher.group(1));
      if (value == null) {
        throw CdsHookRequest.badRequest("The hook context has no " + matcher.group(1));
      }
      if (!ID_OR_REFERENCE.matcher(value).matches()) {
        throw CdsHookRequest.badRequest(
            "The hook context " + matcher.group(1) + " is not a FHIR id or reference");
      }
      matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(rendered);
    return rendered.toString();
  }
}
