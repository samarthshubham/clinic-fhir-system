package io.github.samarthshubham.payer.cdshooks;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public enum CrdHook {
  ORDER_SIGN(
      "order-sign-crd",
      "order-sign",
      "Praxis Health Plan coverage requirements on order sign",
      "Tells whether the signed orders are covered and need prior authorization or documentation,"
          + " and adds the CRD coverage-information extension to each order.",
      true),
  ORDER_SELECT(
      "order-select-crd",
      "order-select",
      "Praxis Health Plan coverage requirements on order select",
      "Tells early whether the selected orders are covered and need prior authorization, and adds"
          + " the CRD coverage-information extension to each selected order.",
      false),
  APPOINTMENT_BOOK(
      "appointment-book-crd",
      "appointment-book",
      "Praxis Health Plan coverage requirements on appointment booking",
      "Tells whether the booked appointments are covered and need prior authorization or"
          + " documentation, and adds the CRD coverage-information extension to each appointment.",
      true),
  ENCOUNTER_START(
      "encounter-start-crd",
      "encounter-start",
      "Praxis Health Plan coverage requirements on encounter start",
      "Tells whether the starting encounter is covered and needs prior authorization or"
          + " documentation, and adds the CRD coverage-information extension to the encounter.",
      true),
  ORDER_DISPATCH(
      "order-dispatch-crd",
      "order-dispatch",
      "Praxis Health Plan coverage requirements on order dispatch",
      "Tells the performer whether the dispatched orders are covered and need prior authorization"
          + " or documentation, and adds the CRD coverage-information extension to each order.",
      true);

  public static final String PATIENT_PREFETCH = "patient";
  public static final String COVERAGE_PREFETCH = "coverage";
  public static final String PRACTITIONER_PREFETCH = "practitioner";
  public static final String ENCOUNTER_PREFETCH = "encounter";
  public static final String DISPATCHED_ORDERS_PREFETCH = "dispatchedOrders";

  static final Map<String, String> PREFETCH = prefetchTemplates();

  private final String serviceId;
  private final String hook;
  private final String title;
  private final String description;
  private final boolean primary;

  CrdHook(String serviceId, String hook, String title, String description, boolean primary) {
    this.serviceId = serviceId;
    this.hook = hook;
    this.title = title;
    this.description = description;
    this.primary = primary;
  }

  public static Optional<CrdHook> byServiceId(String serviceId) {
    return Arrays.stream(values()).filter(hook -> hook.serviceId.equals(serviceId)).findFirst();
  }

  public String serviceId() {
    return serviceId;
  }

  public String hook() {
    return hook;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  /** A primary hook may ask for documentation; CRD 2.2 limits order-select to coverage and PA. */
  public boolean primary() {
    return primary;
  }

  public Map<String, String> prefetch() {
    Map<String, String> templates = new LinkedHashMap<>(PREFETCH);
    switch (this) {
      case ENCOUNTER_START ->
          templates.put(ENCOUNTER_PREFETCH, "Encounter/{{context.encounterId}}");
      case ORDER_DISPATCH -> {
        templates.remove(PRACTITIONER_PREFETCH);
        templates.put(
            DISPATCHED_ORDERS_PREFETCH,
            "ServiceRequest?_id={{context.dispatchedOrders.ServiceRequest.id}}");
      }
      default -> {}
    }
    return Collections.unmodifiableMap(templates);
  }

  static String prefetchTemplate(String key) {
    return Arrays.stream(values())
        .map(hook -> hook.prefetch().get(key))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static Map<String, String> prefetchTemplates() {
    Map<String, String> templates = new LinkedHashMap<>();
    templates.put(PATIENT_PREFETCH, "Patient/{{context.patientId}}");
    templates.put(COVERAGE_PREFETCH, "Coverage?patient={{context.patientId}}&status=active");
    templates.put(PRACTITIONER_PREFETCH, "{{context.userId}}");
    return Collections.unmodifiableMap(templates);
  }
}
