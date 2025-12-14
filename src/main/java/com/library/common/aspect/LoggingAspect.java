package com.library.common.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Common AOP Aspect for logging method execution across all services.
 * Automatically logs method entry, exit, execution time, and errors.
 */
@Aspect
@Component
@Order(10)
public class LoggingAspect {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    
    /**
     * Logs execution of all service and controller methods.
     * Uses a broad pointcut that matches com.library.*.service and com.library.*.controller packages.
     */
    @Around("execution(* com.library..service..*(..)) || " +
            "execution(* com.library..controller..*(..)) || " +
            "execution(* com.library..listener..*(..))")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        
        logger.info("➡️  [{}] Calling method: {}", className, methodName);
        
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;
            logger.info("✅ [{}] Method {} completed in {}ms", className, methodName, executionTime);
            return result;
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.error("❌ [{}] Method {} failed after {}ms: {}", className, methodName, executionTime, e.getMessage());
            throw e;
        }
    }
}
