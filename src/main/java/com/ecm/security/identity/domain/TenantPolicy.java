package com.ecm.security.identity.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;

/**
 * Represents tenant-specific security and authorization policies.
 * Supports ABAC/ReBAC policy definitions with precedence rules.
 */
@Entity
@Table(name = "tenant_policies", indexes = {
    @Index(name = "idx_policies_tenant_type", columnList = "tenant_id, policy_type"),
    @Index(name = "idx_policies_status", columnList = "status"),
    @Index(name = "idx_policies_priority", columnList = "priority")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantPolicy extends BaseEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    @NotBlank
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false)
    private PolicyType policyType;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false)
    private Effect effect;
    
    @NotNull
    @Column(name = "priority", nullable = false)
    private Integer priority = 1000;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PolicyStatus status = PolicyStatus.ACTIVE;
    
    @Column(name = "policy_document", columnDefinition = "jsonb")
    private String policyDocument;
    
    @Column(name = "rego_policy", columnDefinition = "text")
    private String regoPolicy;
    
    @Column(name = "subjects", columnDefinition = "text[]")
    private String[] subjects;
    
    @Column(name = "resources", columnDefinition = "text[]")
    private String[] resources;
    
    @Column(name = "actions", columnDefinition = "text[]")
    private String[] actions;
    
    @Column(name = "conditions", columnDefinition = "jsonb")
    private String conditions;
    
    @Column(name = "time_restrictions", columnDefinition = "jsonb")
    private String timeRestrictions;
    
    @Column(name = "ip_restrictions", columnDefinition = "text[]")
    private String[] ipRestrictions;
    
    @Column(name = "device_restrictions", columnDefinition = "jsonb")
    private String deviceRestrictions;
    
    @Column(name = "risk_level_max")
    private String riskLevelMax;
    
    @Column(name = "mfa_required", nullable = false)
    private Boolean mfaRequired = false;
    
    @Column(name = "step_up_required", nullable = false)
    private Boolean stepUpRequired = false;
    
    @Column(name = "consent_required", nullable = false)
    private Boolean consentRequired = false;
    
    @Column(name = "audit_level", length = 20)
    private String auditLevel = "STANDARD";
    
    @Column(name = "cache_ttl_seconds")
    private Integer cacheTtlSeconds = 300;
    
    @Column(name = "break_glass_eligible", nullable = false)
    private Boolean breakGlassEligible = false;
    
    @Column(name = "emergency_override", nullable = false)
    private Boolean emergencyOverride = false;
    
    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    public enum PolicyType {
        AUTHENTICATION,
        AUTHORIZATION,
        SESSION_MANAGEMENT,
        DATA_ACCESS,
        ADMIN_ACCESS,
        COMPLIANCE,
        SECURITY_BASELINE,
        RISK_ASSESSMENT,
        DEVICE_TRUST,
        NETWORK_ACCESS
    }
    
    public enum Effect {
        ALLOW,
        DENY,
        AUDIT_ONLY,
        WARNING
    }
    
    public enum PolicyStatus {
        ACTIVE,
        INACTIVE,
        DRAFT,
        TESTING,
        DEPRECATED
    }
    
    public boolean isDenyPolicy() {
        return effect == Effect.DENY;
    }
    
    public boolean isAllowPolicy() {
        return effect == Effect.ALLOW;
    }
    
    public boolean isActive() {
        return status == PolicyStatus.ACTIVE;
    }
    
    public boolean requiresHighPriority() {
        return effect == Effect.DENY || breakGlassEligible || emergencyOverride;
    }
    
    public boolean appliesToSubject(String subject) {
        if (subjects == null || subjects.length == 0) {
            return true; // Apply to all if no specific subjects
        }
        
        for (String policySubject : subjects) {
            if (policySubject.equals("*") || policySubject.equals(subject)) {
                return true;
            }
            // Support wildcard matching
            if (policySubject.endsWith("*") && 
                subject.startsWith(policySubject.substring(0, policySubject.length() - 1))) {
                return true;
            }
        }
        return false;
    }
    
    public boolean appliesToResource(String resource) {
        if (resources == null || resources.length == 0) {
            return true; // Apply to all if no specific resources
        }
        
        for (String policyResource : resources) {
            if (policyResource.equals("*") || policyResource.equals(resource)) {
                return true;
            }
            // Support wildcard matching
            if (policyResource.endsWith("*") && 
                resource.startsWith(policyResource.substring(0, policyResource.length() - 1))) {
                return true;
            }
        }
        return false;
    }
    
    public boolean appliesToAction(String action) {
        if (actions == null || actions.length == 0) {
            return true; // Apply to all if no specific actions
        }
        
        for (String policyAction : actions) {
            if (policyAction.equals("*") || policyAction.equals(action)) {
                return true;
            }
            // Support wildcard matching
            if (policyAction.endsWith("*") && 
                action.startsWith(policyAction.substring(0, policyAction.length() - 1))) {
                return true;
            }
        }
        return false;
    }
}
