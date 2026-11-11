package io.github.samarthshubham.payer.cdshooks;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;

record CdsHookRequest(
    String hook,
    String hookInstance,
    String fhirServer,
    String accessToken,
    JsonNode context,
    JsonNode prefetch) {

  static CdsHookRequest from(JsonNode body) {
    if (body == null || !body.isObject()) {
      throw badRequest("The hook request must be a JSON object");
    }
    JsonNode context = body.path("context");
    if (!context.isObject()) {
      throw badRequest("The hook request has no context");
    }
    return new CdsHookRequest(
        text(body, "hook"),
        text(body, "hookInstance"),
        text(body, "fhirServer"),
        text(body.path("fhirAuthorization"), "access_token"),
        context,
        body.path("prefetch"));
  }

  String contextValue(String name) {
    return text(context, name);
  }

  JsonNode draftOrders() {
    return context.path("draftOrders");
  }

  JsonNode appointments() {
    return context.path("appointments");
  }

  List<String> selections() {
    List<String> selections = new ArrayList<>();
    context.path("selections").forEach(selection -> selections.add(selection.asText()));
    return selections;
  }

  JsonNode prefetched(String key) {
    JsonNode value = prefetch.path(key);
    return value.isObject() ? value : null;
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.path(field);
    return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
  }

  static CdsHookException badRequest(String message) {
    return new CdsHookException(HttpStatus.BAD_REQUEST, message);
  }
}
