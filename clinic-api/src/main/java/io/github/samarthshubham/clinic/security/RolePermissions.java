package io.github.samarthshubham.clinic.security;

import static io.github.samarthshubham.clinic.security.Permission.ALERT_READ;
import static io.github.samarthshubham.clinic.security.Permission.ALERT_WRITE;
import static io.github.samarthshubham.clinic.security.Permission.APPOINTMENT_READ;
import static io.github.samarthshubham.clinic.security.Permission.APPOINTMENT_WRITE;
import static io.github.samarthshubham.clinic.security.Permission.CAREPLAN_WRITE;
import static io.github.samarthshubham.clinic.security.Permission.CLINICAL_READ;
import static io.github.samarthshubham.clinic.security.Permission.CONDITION_WRITE;
import static io.github.samarthshubham.clinic.security.Permission.ENCOUNTER_WRITE;
import static io.github.samarthshubham.clinic.security.Permission.MEDICATION_WRITE;
import static io.github.samarthshubham.clinic.security.Permission.ORDER_WRITE;
import static io.github.samarthshubham.clinic.security.Permission.PATIENT_READ;
import static io.github.samarthshubham.clinic.security.Permission.PATIENT_WRITE;
import static io.github.samarthshubham.clinic.security.Permission.PRACTITIONER_READ;
import static io.github.samarthshubham.clinic.security.Permission.VITALS_WRITE;

import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public final class RolePermissions {

  private static final Set<Permission> FRONT_DESK_PERMISSIONS =
      EnumSet.of(
          PATIENT_READ, PATIENT_WRITE, APPOINTMENT_READ, APPOINTMENT_WRITE, PRACTITIONER_READ);

  private static final Set<Permission> CLINICIAN_PERMISSIONS =
      EnumSet.of(
          PATIENT_READ,
          PATIENT_WRITE,
          APPOINTMENT_READ,
          APPOINTMENT_WRITE,
          PRACTITIONER_READ,
          CLINICAL_READ,
          ENCOUNTER_WRITE,
          VITALS_WRITE,
          CONDITION_WRITE,
          MEDICATION_WRITE,
          ORDER_WRITE,
          CAREPLAN_WRITE,
          ALERT_READ,
          ALERT_WRITE);

  private static final Map<Role, Set<Permission>> PERMISSIONS =
      Map.of(
          Role.ADMIN,
          EnumSet.allOf(Permission.class),
          Role.CLINICIAN,
          CLINICIAN_PERMISSIONS,
          Role.FRONT_DESK,
          FRONT_DESK_PERMISSIONS,
          Role.PATIENT,
          EnumSet.noneOf(Permission.class));

  private RolePermissions() {}

  public static Set<Permission> permissionsFor(Role role) {
    return Set.copyOf(PERMISSIONS.get(role));
  }

  public static Set<GrantedAuthority> authoritiesFor(Collection<Role> roles) {
    Set<GrantedAuthority> authorities = new LinkedHashSet<>();
    for (Role role : roles) {
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
      authorities.addAll(PERMISSIONS.get(role));
    }
    return authorities;
  }
}
