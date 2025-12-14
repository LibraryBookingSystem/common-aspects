package com.library.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify required role(s) for a controller method.
 * Used with AuthorizationAspect for declarative role-based authorization.
 * 
 * Usage:
 * - @RequiresRole({"ADMIN"}) - Admin only
 * - @RequiresRole({"ADMIN", "FACULTY"}) - Admin or Faculty
 * - @RequiresRole - Any authenticated user
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {
    /**
     * Required role(s). Method accessible if user has any of these roles.
     * Empty array means any authenticated user can access.
     */
    String[] value() default {};
    
    /**
     * Whether ADMIN role should bypass this check (always allow admins).
     */
    boolean adminBypass() default true;
}
