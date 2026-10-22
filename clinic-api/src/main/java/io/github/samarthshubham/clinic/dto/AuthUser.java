package io.github.samarthshubham.clinic.dto;

import io.github.samarthshubham.clinic.security.Role;
import java.util.List;

public record AuthUser(
    String username, String name, String title, String practitionerId, List<Role> roles) {}
