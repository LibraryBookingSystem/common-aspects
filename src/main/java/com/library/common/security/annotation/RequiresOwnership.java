package com.library.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to specify resource ownership check.
 * Used with AuthorizationAspect to verify user owns the resource or is an admin.
 * 
 * Usage:
 * - @RequiresOwnership(resourceIdParam = "id") - Check if userId matches path param "id"
 * - @RequiresOwnership(resourceIdParam = "userId") - Check if userId matches path param "userId"
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresOwnership {
    /**
     * Parameter name that contains the resource ID to check ownership against.
     */
    String resourceIdParam() default "id";
    
    /**
     * Whether to check ownership by userId (true) or username (false).
     */
    boolean byUserId() default true;
    
    /**
     * Whether ADMIN role should bypass ownership check.
     */
    boolean adminBypass() default true;
}
