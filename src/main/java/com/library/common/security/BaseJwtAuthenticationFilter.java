package com.library.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Base JWT Authentication Filter that can be extended by each service.
 * Handles JWT token extraction, validation, and security context setup.
 * Services should extend this and override getPublicEndpoints() to specify their public paths.
 */
public abstract class BaseJwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger logger = LoggerFactory.getLogger(BaseJwtAuthenticationFilter.class);
    
    @Autowired
    protected JwtUtil jwtUtil;
    
    /**
     * Override this method to specify public endpoints for your service.
     * @return Set of public endpoint paths that don't require authentication
     */
    protected abstract Set<String> getPublicEndpoints();
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Skip JWT validation for public endpoints
        if (isPublicEndpoint(path)) {
            chain.doFilter(request, response);
            return;
        }
        
        try {
            String token = extractToken(request);
            
            if (token != null && jwtUtil.validateToken(token)) {
                String role = jwtUtil.extractRole(token);
                Long userId = jwtUtil.extractUserId(token);
                String username = jwtUtil.extractUsername(token);
                
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                    new SimpleGrantedAuthority("ROLE_" + role)
                );
                
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // Set user details in request attributes for easy access
                request.setAttribute("userId", userId);
                request.setAttribute("userRole", role);
                request.setAttribute("username", username);
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                logger.debug("JWT validated for user: {} with role: {}", username, role);
            } else {
                logger.warn("Invalid or missing JWT token for path: {}", path);
                sendUnauthorizedResponse(response, "Invalid or missing JWT token");
                return;
            }
        } catch (Exception e) {
            logger.error("Error processing JWT token", e);
            sendUnauthorizedResponse(response, "JWT token processing failed");
            return;
        }
        
        chain.doFilter(request, response);
    }
    
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    private boolean isPublicEndpoint(String path) {
        // Common public endpoints
        if (path.equals("/health") || path.equals("/actuator/health")) {
            return true;
        }
        // Service-specific public endpoints
        return getPublicEndpoints().contains(path);
    }
    
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}");
    }
}
