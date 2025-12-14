# Library Common Aspects

Shared AOP aspects, security components, and exceptions for Library System microservices.

## Quick Start (Docker)

From the workspace root directory:

```powershell
# Build everything (common-aspects + all services)
.\build-all.ps1

# Start services
cd docker-compose
docker-compose up -d
```

## Components

### Aspects

- `LoggingAspect` - Automatic method logging with execution time

### Security

- `JwtUtil` - JWT token validation and claim extraction
- `BaseJwtAuthenticationFilter` - Base filter for JWT authentication (extend per service)
- `BaseAuthorizationAspect` - Role and ownership authorization checks

### Annotations

- `@RequiresRole` - Declarative role-based access control
- `@RequiresOwnership` - Resource ownership validation

### Exceptions

- `ForbiddenException` - Access denied exception
- `GlobalExceptionHandler` - Base exception handler (extend per service)

## Package Structure

```
com.library.common
├── aspect
│   └── LoggingAspect.java
├── exception
│   ├── ForbiddenException.java
│   └── GlobalExceptionHandler.java
└── security
    ├── JwtUtil.java
    ├── BaseJwtAuthenticationFilter.java
    ├── annotation
    │   ├── RequiresRole.java
    │   └── RequiresOwnership.java
    └── aspect
        └── BaseAuthorizationAspect.java
```

## Services Using This Library

All services have been migrated to use common-aspects:

- user-service
- auth-service
- booking-service (+ custom @RequiresBookingOwnership)
- catalog-service
- policy-service
- notification-service (+ custom @RequiresNotificationOwnership)
- analytics-service

## Usage Examples

### JWT Authentication Filter

```java
@Component
public class JwtAuthenticationFilter extends BaseJwtAuthenticationFilter {
    @Override
    protected Set<String> getPublicEndpoints() {
        return Set.of("/api/health", "/api/your-service/public");
    }
}
```

### Authorization Annotations

```java
@GetMapping
@RequiresRole  // Any authenticated user
public List<Resource> getAll() { ... }

@PostMapping
@RequiresRole({"ADMIN"})  // Admin only
public Resource create(@RequestBody Resource r) { ... }

@GetMapping("/{id}")
@RequiresRole
@RequiresOwnership(resourceIdParam = "id")  // Owner or admin
public Resource getById(@PathVariable Long id) { ... }
```

### Exception Handler

```java
@RestControllerAdvice
public class ServiceExceptionHandler extends GlobalExceptionHandler {
    @ExceptionHandler(YourCustomException.class)
    public ResponseEntity<?> handleCustomException(YourCustomException e) {
        // Handle service-specific exceptions
    }
}
```
