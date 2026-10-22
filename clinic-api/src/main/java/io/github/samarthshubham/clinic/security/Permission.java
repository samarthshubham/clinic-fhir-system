package io.github.samarthshubham.clinic.security;

import org.springframework.security.core.GrantedAuthority;

public enum Permission implements GrantedAuthority {
  PATIENT_READ("patient:read"),
  PATIENT_WRITE("patient:write"),
  APPOINTMENT_READ("appointment:read"),
  APPOINTMENT_WRITE("appointment:write"),
  PRACTITIONER_READ("practitioner:read"),
  PRACTITIONER_WRITE("practitioner:write"),
  CLINICAL_READ("clinical:read"),
  ENCOUNTER_WRITE("encounter:write"),
  VITALS_WRITE("vitals:write"),
  CONDITION_WRITE("condition:write"),
  MEDICATION_WRITE("medication:write"),
  ORDER_WRITE("order:write"),
  CAREPLAN_WRITE("careplan:write");

  private final String authority;

  Permission(String authority) {
    this.authority = authority;
  }

  @Override
  public String getAuthority() {
    return authority;
  }
}
