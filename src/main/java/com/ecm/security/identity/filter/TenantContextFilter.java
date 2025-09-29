package com.ecm.security.identity.filter;

import com.ecm.security.identity.service.TenantContextService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that establishes tenant context for each request.
 * Ensures proper tenant isolation and data segregation across the application.
 */
@Component
@Order(1)
@Slf4j
@RequiredArgsConstructor
public class TenantContextFilter extends OncePerRequestFilter {
    
    private final TenantContextService tenantContextService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        try {
            // Resolve tenant context from the request
            TenantContextService.TenantContext context = tenantContextService.resolveTenantFromRequest();
            
            if (context == null) {
                log.warn("No tenant context could be resolved for request: {}", request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Invalid or missing tenant context\"}");
                response.setContentType("application/json");
                return;
            }
            
            // Set the tenant context for this request
            tenantContextService.setCurrentContext(context);
            
            log.debug("Set tenant context for request: {} -> {}", 
                     request.getRequestURI(), context.getTenantCode());
            
            // Add tenant information to response headers for debugging
            response.setHeader("X-Current-Tenant", context.getTenantCode());
            
            // Continue with the filter chain
            filterChain.doFilter(request, response);
            
        } catch (Exception e) {
            log.error("Error processing tenant context", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal server error\"}");
            response.setContentType("application/json");
            
        } finally {
            // Always clear the tenant context after request processing
            tenantContextService.clearCurrentContext();
        }
    }
    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // Skip tenant filtering for certain endpoints
        return path.startsWith("/actuator/") ||
               path.startsWith("/oauth2/") ||
               path.startsWith("/.well-known/") ||
               path.equals("/") ||
               path.equals("/health") ||
               path.equals("/error") ||
               path.startsWith("/webjars/") ||
               path.startsWith("/assets/") ||
               path.equals("/favicon.ico");
    }
}
