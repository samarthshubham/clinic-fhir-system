package io.github.samarthshubham.payer.cdshooks;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
      true);

  public static final String PATIENT_PREFETCH = "patient";
  public static final String COVERAGE_PREFETCH = "coverage";
  public static final String PRACTITIONER_PREFETCH = "practitioner";

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

  private static Map<String, String> prefetchTemplates() {
    Map<String, String> templates = new LinkedHashMap<>();
    templates.put(PATIENT_PREFETCH, "Patient/{{context.patientId}}");
    templates.put(COVERAGE_PREFETCH, "Coverage?patient={{context.patientId}}&status=active");
    templates.put(PRACTITIONER_PREFETCH, "{{context.userId}}");
    return Collections.unmodifiableMap(templates);
  }
}
