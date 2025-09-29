package com.ecm.security.identity.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tenant-specific settings and policies.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class TenantSettings {
    
    private PasswordPolicy passwordPolicy;
    private SessionPolicy sessionPolicy;
    private MfaPolicy mfaPolicy;
    private RiskPolicy riskPolicy;
    private boolean enableAuditLogging;
    private boolean enableRiskAssessment;
    private boolean enableMfa;
    
    /**
     * Create TenantSettings from JSON string.
     */
    public static TenantSettings fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return getDefaultSettings();
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, TenantSettings.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse tenant settings JSON, using defaults: {}", e.getMessage());
            return getDefaultSettings();
        }
    }
    
    /**
     * Convert TenantSettings to JSON string.
     */
    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tenant settings to JSON: {}", e.getMessage());
            return "{}";
        }
    }
    
    /**
     * Get default tenant settings.
     */
    public static TenantSettings getDefaultSettings() {
        return TenantSettings.builder()
            .passwordPolicy(PasswordPolicy.getDefault())
            .sessionPolicy(SessionPolicy.getDefault())
            .mfaPolicy(MfaPolicy.getDefault())
            .riskPolicy(RiskPolicy.getDefault())
            .enableAuditLogging(true)
            .enableRiskAssessment(true)
            .enableMfa(false)
            .build();
    }
}
