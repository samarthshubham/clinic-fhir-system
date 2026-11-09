package io.github.samarthshubham.payer.dto;

import io.github.samarthshubham.payer.security.Role;
import java.util.List;

public record AuthUser(String username, String name, String title, List<Role> roles) {}
