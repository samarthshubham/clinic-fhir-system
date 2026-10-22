package io.github.samarthshubham.clinic.security;

import io.github.samarthshubham.clinic.config.JwtConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class PatientAccess {

  private static final String PATIENT_AUTHORITY = "ROLE_" + Role.PATIENT.name();

  public boolean isSelf(String patientId) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return patientId != null
        && isPatient()
        && authentication.getPrincipal() instanceof Jwt jwt
        && patientId.equals(jwt.getClaimAsString(JwtConfig.PATIENT_ID_CLAIM));
  }

  public boolean isPatient() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(authority -> PATIENT_AUTHORITY.equals(authority.getAuthority()));
  }
}
