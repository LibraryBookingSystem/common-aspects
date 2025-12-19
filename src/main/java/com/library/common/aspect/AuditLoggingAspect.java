package com.library.common.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * AOP Aspect for automatic audit logging
 * Intercepts controller methods and publishes audit events to RabbitMQ
 * Only logs if RabbitTemplate is available (optional dependency)
 */
@Aspect
@Component
@Order(5) // Run before LoggingAspect
public class AuditLoggingAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditLoggingAspect.class);
    
    private static final String AUDIT_EXCHANGE = "audit.events";
    
    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Intercept all controller methods for audit logging
     */
    @Around("execution(* com.library..controller..*(..))")
    public Object auditControllerMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        // Skip if RabbitTemplate is not available (audit logging is optional)
        if (rabbitTemplate == null) {
            return joinPoint.proceed();
        }
        
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        
        HttpServletRequest request = attributes.getRequest();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        
        // Extract user info from request attributes (set by JWT filter)
        Long userId = getUserId(request);
        String username = getUsername(request);
        String userRole = getUserRole(request);
        String ipAddress = getClientIpAddress(request);
        
        // Determine action type and resource type from method name and class
        String actionType = determineActionType(methodName);
        String resourceType = determineResourceType(className);
        
        // Extract resource ID from method arguments if possible
        Long resourceId = extractResourceId(joinPoint.getArgs());
        String resourceName = extractResourceName(joinPoint.getArgs());
        
        // Build description
        String description = buildDescription(className, methodName, resourceName, resourceId);
        
        boolean success = false;
        String errorMessage = null;
        Object result = null;
        
        try {
            result = joinPoint.proceed();
            success = true;
            
            // Publish audit event
            publishAuditEvent(userId, username, userRole, actionType, resourceType,
                            resourceId, resourceName, description, ipAddress, success, null);
            
            return result;
        } catch (Exception e) {
            success = false;
            errorMessage = e.getMessage();
            
            // Publish failed audit event
            publishAuditEvent(userId, username, userRole, actionType, resourceType,
                            resourceId, resourceName, description, ipAddress, success, errorMessage);
            
            throw e;
        }
    }
    
    /**
     * Publish audit event to RabbitMQ
     */
    private void publishAuditEvent(Long userId, String username, String userRole,
                                  String actionType, String resourceType, Long resourceId,
                                  String resourceName, String description, String ipAddress,
                                  Boolean success, String errorMessage) {
        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("userId", userId);
            auditEvent.put("username", username);
            auditEvent.put("userRole", userRole);
            auditEvent.put("actionType", actionType);
            auditEvent.put("resourceType", resourceType);
            auditEvent.put("resourceId", resourceId);
            auditEvent.put("resourceName", resourceName);
            auditEvent.put("description", description);
            auditEvent.put("ipAddress", ipAddress);
            auditEvent.put("success", success);
            auditEvent.put("errorMessage", errorMessage);
            auditEvent.put("timestamp", java.time.LocalDateTime.now().toString());
            
            rabbitTemplate.convertAndSend(AUDIT_EXCHANGE, "audit.log", auditEvent);
            
            logger.debug("Published audit event: {} - {} - {}", actionType, resourceType, resourceId);
        } catch (Exception e) {
            // Don't fail the request if audit logging fails
            logger.error("Failed to publish audit event: {}", e.getMessage());
        }
    }
    
    /**
     * Extract user ID from request
     */
    private Long getUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long) {
            return (Long) userId;
        } else if (userId instanceof Integer) {
            return ((Integer) userId).longValue();
        }
        return null;
    }
    
    /**
     * Extract username from request
     */
    private String getUsername(HttpServletRequest request) {
        return (String) request.getAttribute("username");
    }
    
    /**
     * Extract user role from request
     */
    private String getUserRole(HttpServletRequest request) {
        return (String) request.getAttribute("userRole");
    }
    
    /**
     * Get client IP address
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
    
    /**
     * Determine action type from method name
     */
    private String determineActionType(String methodName) {
        String lowerMethod = methodName.toLowerCase();
        if (lowerMethod.contains("create") || lowerMethod.contains("register") || lowerMethod.contains("add")) {
            return "CREATE";
        } else if (lowerMethod.contains("update") || lowerMethod.contains("edit") || lowerMethod.contains("modify")) {
            return "UPDATE";
        } else if (lowerMethod.contains("delete") || lowerMethod.contains("remove")) {
            return "DELETE";
        } else if (lowerMethod.contains("login") || lowerMethod.contains("authenticate")) {
            return "LOGIN";
        } else if (lowerMethod.contains("logout")) {
            return "LOGOUT";
        } else if (lowerMethod.contains("check") || lowerMethod.contains("checkin")) {
            return "CHECK_IN";
        } else if (lowerMethod.contains("cancel")) {
            return "CANCEL";
        } else if (lowerMethod.contains("approve")) {
            return "APPROVE";
        } else if (lowerMethod.contains("restrict") || lowerMethod.contains("unrestrict")) {
            return "MANAGE_USER";
        } else if (lowerMethod.contains("get") || lowerMethod.contains("list") || lowerMethod.contains("find")) {
            return "VIEW";
        }
        return "OTHER";
    }
    
    /**
     * Determine resource type from class name
     */
    private String determineResourceType(String className) {
        String lowerClass = className.toLowerCase();
        if (lowerClass.contains("resource")) {
            return "RESOURCE";
        } else if (lowerClass.contains("booking")) {
            return "BOOKING";
        } else if (lowerClass.contains("policy")) {
            return "POLICY";
        } else if (lowerClass.contains("user")) {
            return "USER";
        } else if (lowerClass.contains("notification")) {
            return "NOTIFICATION";
        } else if (lowerClass.contains("auth") || lowerClass.contains("login")) {
            return "AUTH";
        }
        return "OTHER";
    }
    
    /**
     * Extract resource ID from method arguments
     */
    private Long extractResourceId(Object[] args) {
        if (args == null) {
            return null;
        }
        
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            } else if (arg instanceof Integer) {
                return ((Integer) arg).longValue();
            } else if (arg instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) arg;
                Object id = map.get("id");
                if (id instanceof Long) {
                    return (Long) id;
                } else if (id instanceof Integer) {
                    return ((Integer) id).longValue();
                }
            }
        }
        return null;
    }
    
    /**
     * Extract resource name from method arguments
     */
    private String extractResourceName(Object[] args) {
        if (args == null) {
            return null;
        }
        
        for (Object arg : args) {
            if (arg instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) arg;
                Object name = map.get("name");
                if (name != null) {
                    return name.toString();
                }
            } else if (arg != null && arg.getClass().getSimpleName().contains("Request")) {
                // Try to get name field via reflection (simplified)
                try {
                    java.lang.reflect.Field nameField = arg.getClass().getDeclaredField("name");
                    nameField.setAccessible(true);
                    Object name = nameField.get(arg);
                    if (name != null) {
                        return name.toString();
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        return null;
    }
    
    /**
     * Build human-readable description
     */
    private String buildDescription(String className, String methodName, String resourceName, Long resourceId) {
        StringBuilder desc = new StringBuilder();
        
        String action = determineActionType(methodName);
        String resource = determineResourceType(className);
        
        desc.append(action).append(" ").append(resource);
        
        if (resourceName != null) {
            desc.append(": ").append(resourceName);
        } else if (resourceId != null) {
            desc.append(" (ID: ").append(resourceId).append(")");
        }
        
        return desc.toString();
    }
}
