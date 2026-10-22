package io.github.samarthshubham.clinic.support;

import io.github.samarthshubham.clinic.security.Role;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.test.context.support.WithSecurityContext;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@WithSecurityContext(factory = WithRoleSecurityContextFactory.class)
public @interface WithRole {
  Role value();
}
