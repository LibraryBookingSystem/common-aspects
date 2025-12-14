package com.library.common.exception;

/**
 * Exception thrown when user doesn't have permission to perform an action.
 * Used by authorization aspects when access is denied.
 */
public class ForbiddenException extends RuntimeException {
    
    public ForbiddenException(String message) {
        super(message);
    }
    
    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
