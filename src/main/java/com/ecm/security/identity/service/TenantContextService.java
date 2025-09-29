package com.ecm.security.identity.service;

import com.ecm.security.identity.domain.Tenant;
import com.ecm.security.identity.domain.TenantContext;
import com.ecm.security.identity.domain.TenantSettings;
import com.ecm.security.identity.domain.PasswordPolicy;
import com.ecm.security.identity.domain.SessionPolicy;
import com.ecm.security.identity.domain.MfaPolicy;
import com.ecm.security.identity.domain.RiskPolicy;
import com.ecm.security.identity.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing tenant context and multi-tenancy operations.
 * Handles tenant resolution, context switching, and data isolation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TenantContextService {
    
    private final TenantRepository tenantRepository;
    private final ThreadLocal<TenantContext> tenantContext = new ThreadLocal<>();
    
    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_PARAM = "tenantId";
    private static final String DEFAULT_TENANT_CODE = "default";
    
    /**
     * Gets the current tenant context.
     */
    public TenantContext getCurrentContext() {
        TenantContext context = tenantContext.get();
        if (context == null) {
            context = resolveTenantFromRequest();
            setCurrentContext(context);
        }
        return context;
    }
    
    /**
     * Sets the current tenant context.
     */
    public void setCurrentContext(TenantContext context) {
        tenantContext.set(context);
        log.debug("Set tenant context: {}", context.getTenantCode());
    }
    
    /**
     * Clears the current tenant context.
     */
    public void clearCurrentContext() {
        tenantContext.remove();
    }
    
    /**
     * Gets the current tenant ID.
     */
    public UUID getCurrentTenantId() {
        TenantContext context = getCurrentContext();
        return context != null ? context.getTenantId() : null;
    }
    
    /**
     * Gets the current tenant.
     */
    @Cacheable(value = "tenants", key = "#root.methodName + ':' + getCurrentTenantId()")
    public Optional<Tenant> getCurrentTenant() {
        UUID tenantId = getCurrentTenantId();
        if (tenantId == null) {
            return Optional.empty();
        }
        return tenantRepository.findById(tenantId);
    }
    
    /**
     * Resolves tenant from the current HTTP request.
     */
    public TenantContext resolveTenantFromRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return getDefaultTenantContext();
        }
        
        HttpServletRequest request = attributes.getRequest();
        
        // Try to get tenant from header
        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId != null && !tenantId.isEmpty()) {
            try {
                return resolveTenantById(UUID.fromString(tenantId));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tenant ID in header: {}", tenantId);
            }
        }
        
        // Try to get tenant from request parameter
        tenantId = request.getParameter(TENANT_PARAM);
        if (tenantId != null && !tenantId.isEmpty()) {
            try {
                return resolveTenantById(UUID.fromString(tenantId));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tenant ID in parameter: {}", tenantId);
            }
        }
        
        // Try to get tenant from subdomain
        String serverName = request.getServerName();
        if (serverName != null && !serverName.equals("localhost")) {
            String[] parts = serverName.split("\\.");
            if (parts.length > 2) {
                String subdomain = parts[0];
                Optional<Tenant> tenant = findTenantByCode(subdomain);
                if (tenant.isPresent() && tenant.get().isActive()) {
                    return TenantContext.builder()
                        .tenantId(tenant.get().getId())
                        .tenantCode(tenant.get().getTenantCode())
                        .tenantName(tenant.get().getName())
                        .build();
                }
            }
        }
        
        // Try to get tenant from domain
        Optional<Tenant> tenant = findTenantByDomain(serverName);
        if (tenant.isPresent() && tenant.get().isActive()) {
            return TenantContext.builder()
                .tenantId(tenant.get().getId())
                .tenantCode(tenant.get().getTenantCode())
                .tenantName(tenant.get().getName())
                .build();
        }
        
        // Fall back to default tenant
        return getDefaultTenantContext();
    }
    
    /**
     * Resolves tenant by ID.
     */
    public TenantContext resolveTenantById(UUID tenantId) {
        Optional<Tenant> tenant = findTenantById(tenantId);
        if (tenant.isPresent() && tenant.get().isActive()) {
            return TenantContext.builder()
                .tenantId(tenant.get().getId())
                .tenantCode(tenant.get().getTenantCode())
                .tenantName(tenant.get().getName())
                .build();
        }
        
        log.warn("Tenant not found or inactive: {}", tenantId);
        return getDefaultTenantContext();
    }
    
    /**
     * Resolves tenant by code.
     */
    public TenantContext resolveTenantByCode(String tenantCode) {
        Optional<Tenant> tenant = findTenantByCode(tenantCode);
        if (tenant.isPresent() && tenant.get().isActive()) {
            return TenantContext.builder()
                .tenantId(tenant.get().getId())
                .tenantCode(tenant.get().getTenantCode())
                .tenantName(tenant.get().getName())
                .build();
        }
        
        log.warn("Tenant not found or inactive: {}", tenantCode);
        return getDefaultTenantContext();
    }
    
    /**
     * Validates if the current context can access the specified tenant.
     */
    public boolean canAccessTenant(UUID tenantId) {
        TenantContext currentContext = getCurrentContext();
        if (currentContext == null) {
            return false;
        }
        
        // Users can only access their own tenant unless they have cross-tenant permissions
        return currentContext.getTenantId().equals(tenantId);
    }
    
    /**
     * Executes a runnable in the context of a specific tenant.
     */
    public void executeInTenantContext(UUID tenantId, Runnable runnable) {
        TenantContext originalContext = getCurrentContext();
        try {
            TenantContext newContext = resolveTenantById(tenantId);
            setCurrentContext(newContext);
            runnable.run();
        } finally {
            if (originalContext != null) {
                setCurrentContext(originalContext);
            } else {
                clearCurrentContext();
            }
        }
    }
    
    /**
     * Executes a function in the context of a specific tenant.
     */
    public <T> T executeInTenantContext(UUID tenantId, java.util.function.Supplier<T> supplier) {
        TenantContext originalContext = getCurrentContext();
        try {
            TenantContext newContext = resolveTenantById(tenantId);
            setCurrentContext(newContext);
            return supplier.get();
        } finally {
            if (originalContext != null) {
                setCurrentContext(originalContext);
            } else {
                clearCurrentContext();
            }
        }
    }
    
    /**
     * Checks if multi-tenancy is enabled.
     */
    public boolean isMultiTenancyEnabled() {
        // This could be configurable via application properties
        return true;
    }
    
    /**
     * Gets tenant settings as a typed object.
     */
    public TenantSettings getTenantSettings(UUID tenantId) {
        Optional<Tenant> tenant = findTenantById(tenantId);
        if (tenant.isPresent()) {
            return TenantSettings.fromJson(tenant.get().getSettings());
        }
        return TenantSettings.getDefault();
    }
    
    /**
     * Finds tenant by ID with caching.
     */
    @Cacheable(value = "tenants", key = "'id:' + #tenantId")
    public Optional<Tenant> findTenantById(UUID tenantId) {
        return tenantRepository.findById(tenantId);
    }
    
    /**
     * Finds tenant by code with caching.
     */
    @Cacheable(value = "tenants", key = "'code:' + #tenantCode")
    public Optional<Tenant> findTenantByCode(String tenantCode) {
        return tenantRepository.findByTenantCodeAndStatusNot(tenantCode, Tenant.TenantStatus.ARCHIVED);
    }
    
    /**
     * Finds tenant by domain with caching.
     */
    @Cacheable(value = "tenants", key = "'domain:' + #domain")
    public Optional<Tenant> findTenantByDomain(String domain) {
        return tenantRepository.findByDomainAndStatusNot(domain, Tenant.TenantStatus.ARCHIVED);
    }
    
    /**
     * Gets default tenant context.
     */
    private TenantContext getDefaultTenantContext() {
        Optional<Tenant> defaultTenant = findTenantByCode(DEFAULT_TENANT_CODE);
        if (defaultTenant.isPresent() && defaultTenant.get().isActive()) {
            return TenantContext.builder()
                .tenantId(defaultTenant.get().getId())
                .tenantCode(defaultTenant.get().getTenantCode())
                .tenantName(defaultTenant.get().getName())
                .build();
        }
        
        log.error("Default tenant not found or inactive");
        throw new IllegalStateException("Default tenant not available");
    }
    
    /**
     * Represents the current tenant context.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TenantContext {
        private UUID tenantId;
        private String tenantCode;
        private String tenantName;
    }
    
    /**
     * Represents tenant-specific settings.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TenantSettings {
        private PasswordPolicy passwordPolicy;
        private SessionPolicy sessionPolicy;
        private MfaPolicy mfaPolicy;
        private RiskPolicy riskPolicy;
        
        public static TenantSettings fromJson(String json) {
            // Parse JSON settings - implement JSON parsing
            return getDefault();
        }
        
        public static TenantSettings getDefault() {
            return TenantSettings.builder()
                .passwordPolicy(PasswordPolicy.getDefault())
                .sessionPolicy(SessionPolicy.getDefault())
                .mfaPolicy(MfaPolicy.getDefault())
                .riskPolicy(RiskPolicy.getDefault())
                .build();
        }
        
        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class PasswordPolicy {
            private int minLength;
            private boolean requireUppercase;
            private boolean requireLowercase;
            private boolean requireNumbers;
            private boolean requireSpecialChars;
            private int maxAge;
            private int preventReuse;
            
            public static PasswordPolicy getDefault() {
                return PasswordPolicy.builder()
                    .minLength(8)
                    .requireUppercase(true)
                    .requireLowercase(true)
                    .requireNumbers(true)
                    .requireSpecialChars(true)
                    .maxAge(90)
                    .preventReuse(5)
                    .build();
            }
        }
        
        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class SessionPolicy {
            private int maxSessions;
            private int sessionTimeout;
            private int extendedSessionTimeout;
            
            public static SessionPolicy getDefault() {
                return SessionPolicy.builder()
                    .maxSessions(5)
                    .sessionTimeout(900)      // 15 minutes
                    .extendedSessionTimeout(28800)  // 8 hours
                    .build();
            }
        }
        
        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class MfaPolicy {
            private boolean required;
            private String[] allowedMethods;
            private int gracePeriod;
            
            public static MfaPolicy getDefault() {
                return MfaPolicy.builder()
                    .required(false)
                    .allowedMethods(new String[]{"TOTP", "WEBAUTHN", "SMS", "EMAIL"})
                    .gracePeriod(7)
                    .build();
            }
        }
        
        @lombok.Data
        @lombok.Builder
        @lombok.AllArgsConstructor
        @lombok.NoArgsConstructor
        public static class RiskPolicy {
            private boolean enabled;
            private double maxRiskScore;
            private int impossibleTravelThreshold;
            
            public static RiskPolicy getDefault() {
                return RiskPolicy.builder()
                    .enabled(true)
                    .maxRiskScore(70.0)
                    .impossibleTravelThreshold(500)  // km/h
                    .build();
            }
        }
    }
}
