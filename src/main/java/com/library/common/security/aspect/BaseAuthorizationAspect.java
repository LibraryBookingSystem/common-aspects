package com.library.common.security.aspect;

import com.library.common.exception.ForbiddenException;
import com.library.common.security.annotation.RequiresOwnership;
import com.library.common.security.annotation.RequiresRole;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Base AOP Aspect for authorization checks.
 * Handles @RequiresRole and @RequiresOwnership annotations.
 * Services can extend this for custom ownership checks (e.g., booking ownership).
 */
@Aspect
@Component
@Order(1)
public class BaseAuthorizationAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(BaseAuthorizationAspect.class);
    
    /**
     * Check role-based authorization for @RequiresRole annotation.
     */
    @Before("@annotation(com.library.common.security.annotation.RequiresRole)")
    public void checkRoleAuthorization(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequiresRole requiresRole = method.getAnnotation(RequiresRole.class);
        
        if (requiresRole == null) return;
        
        String userRole = getCurrentUserRole();
        if (userRole == null) {
            throw new ForbiddenException("Authentication required");
        }
        
        String[] requiredRoles = requiresRole.value();
        boolean adminBypass = requiresRole.adminBypass();
        
        // Admin bypass
        if (adminBypass && "ADMIN".equals(userRole)) {
            logger.debug("Admin bypass for role check on method: {}", method.getName());
            return;
        }
        
        // If no specific roles required, any authenticated user can access
        if (requiredRoles.length == 0) {
            return;
        }
        
        // Check if user has one of the required roles
        Set<String> requiredRolesSet = Arrays.stream(requiredRoles)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        
        if (!requiredRolesSet.contains(userRole.toUpperCase())) {
            throw new ForbiddenException(
                String.format("Access denied. Required role(s): %s, but user has role: %s", 
                    Arrays.toString(requiredRoles), userRole)
            );
        }
        
        logger.debug("Role authorization passed for user role: {} on method: {}", userRole, method.getName());
    }
    
    /**
     * Check resource ownership for @RequiresOwnership annotation.
     */
    @Before("@annotation(com.library.common.security.annotation.RequiresOwnership)")
    public void checkOwnershipAuthorization(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequiresOwnership annotation = method.getAnnotation(RequiresOwnership.class);
        
        if (annotation == null) return;
        
        String userRole = getCurrentUserRole();
        Long userId = getCurrentUserId();
        String username = getCurrentUsername();
        
        if (userRole == null || userId == null) {
            throw new ForbiddenException("Authentication required");
        }
        
        // Admin bypass
        if (annotation.adminBypass() && "ADMIN".equals(userRole)) {
            logger.debug("Admin bypass for ownership check on method: {}", method.getName());
            return;
        }
        
        // Extract resource identifier from method parameters
        Object resourceId = extractResourceId(joinPoint, method, annotation.resourceIdParam());
        if (resourceId == null) {
            logger.warn("Could not extract resource ID for ownership check on method: {}", method.getName());
            throw new ForbiddenException("Resource ID not found");
        }
        
        // Check ownership
        boolean ownsResource = false;
        if (annotation.byUserId()) {
            if (resourceId instanceof Long) {
                ownsResource = userId.equals((Long) resourceId);
            } else if (resourceId instanceof String) {
                try {
                    Long resourceUserId = Long.parseLong((String) resourceId);
                    ownsResource = userId.equals(resourceUserId);
                } catch (NumberFormatException e) {
                    logger.error("Invalid user ID format for ownership check: {}", resourceId);
                }
            }
        } else {
            if (resourceId instanceof String && username != null) {
                ownsResource = username.equals(resourceId);
            }
        }
        
        if (!ownsResource) {
            throw new ForbiddenException("Access denied. You do not have permission to access this resource");
        }
        
        logger.debug("Ownership authorization passed for user: {} on method: {}", userId, method.getName());
    }
    
    /**
     * Extract resource ID from method parameters.
     */
    protected Object extractResourceId(JoinPoint joinPoint, Method method, String paramName) {
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();
        
        // Find parameter by name
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName)) {
                return args[i];
            }
        }
        
        // Fallback: try common names
        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].getName().toLowerCase();
            if (name.equals("id") || name.equals("userid") || name.equals("username")) {
                return args[i];
            }
        }
        
        return null;
    }
    
    protected String getCurrentUserRole() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return (String) request.getAttribute("userRole");
        }
        return null;
    }
    
    protected Long getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            Object userId = request.getAttribute("userId");
            if (userId instanceof Long) {
                return (Long) userId;
            } else if (userId instanceof Integer) {
                return ((Integer) userId).longValue();
            }
        }
        return null;
    }
    
    protected String getCurrentUsername() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            return (String) request.getAttribute("username");
        }
        return null;
    }
}
