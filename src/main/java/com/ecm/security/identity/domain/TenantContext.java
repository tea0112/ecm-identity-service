package com.ecm.security.identity.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Context object holding current tenant information.
 * Used by TenantContextService to manage multi-tenancy.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TenantContext {
    
    private Long tenantId;
    private String tenantCode;
    private String tenantName;
    private boolean active;
    
    /**
     * Check if this context represents a valid tenant.
     */
    public boolean isValid() {
        return tenantId != null && tenantCode != null && active;
    }
    
    /**
     * Get the tenant identifier for database operations.
     */
    public String getTenantIdentifier() {
        return tenantCode;
    }
}
