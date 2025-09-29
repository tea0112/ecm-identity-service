package com.ecm.security.identity.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Represents user role assignments with time-bound and conditional access support.
 * Supports JIT access, approval workflows, and delegation scenarios.
 */
@Entity
@Table(name = "user_roles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "role_name", "scope"})
}, indexes = {
    @Index(name = "idx_user_roles_user", columnList = "user_id"),
    @Index(name = "idx_user_roles_role", columnList = "role_name"),
    @Index(name = "idx_user_roles_expires", columnList = "expires_at"),
    @Index(name = "idx_user_roles_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class UserRole extends BaseEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotBlank
    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;
    
    @Column(name = "scope", length = 255)
    private String scope; // e.g., "project:123", "organization:456", "global"
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_type", nullable = false)
    private AssignmentType assignmentType = AssignmentType.PERMANENT;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoleStatus status = RoleStatus.ACTIVE;
    
    @Column(name = "granted_by_user_id")
    private String grantedByUserId;
    
    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;
    
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    
    @Column(name = "revoked_at")
    private Instant revokedAt;
    
    @Column(name = "revoked_by_user_id")
    private String revokedByUserId;
    
    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;
    
    @Column(name = "justification", length = 1000)
    private String justification;
    
    @Column(name = "approval_required", nullable = false)
    private Boolean approvalRequired = false;
    
    @Column(name = "approved_by_user_id")
    private String approvedByUserId;
    
    @Column(name = "approved_at")
    private Instant approvedAt;
    
    @Column(name = "approval_workflow_id", length = 255)
    private String approvalWorkflowId;
    
    // Delegation support
    @Column(name = "delegated_from_user_id")
    private String delegatedFromUserId;
    
    @Column(name = "delegation_depth", nullable = false)
    private Integer delegationDepth = 0;
    
    @Column(name = "max_delegation_depth", nullable = false)
    private Integer maxDelegationDepth = 0;
    
    @Column(name = "delegation_restrictions", columnDefinition = "jsonb")
    private String delegationRestrictions;
    
    // Conditional access
    @Column(name = "conditions", columnDefinition = "jsonb")
    private String conditions;
    
    @Column(name = "time_restrictions", columnDefinition = "jsonb")
    private String timeRestrictions;
    
    @Column(name = "ip_restrictions", columnDefinition = "text[]")
    private String[] ipRestrictions;
    
    @Column(name = "device_restrictions", columnDefinition = "jsonb")
    private String deviceRestrictions;
    
    @Column(name = "mfa_required", nullable = false)
    private Boolean mfaRequired = false;
    
    @Column(name = "step_up_required", nullable = false)
    private Boolean stepUpRequired = false;
    
    // Break-glass and emergency access
    @Column(name = "break_glass_role", nullable = false)
    private Boolean breakGlassRole = false;
    
    @Column(name = "emergency_access", nullable = false)
    private Boolean emergencyAccess = false;
    
    @Column(name = "emergency_justification", length = 1000)
    private String emergencyJustification;
    
    @Column(name = "emergency_approved_by")
    private String emergencyApprovedBy;
    
    // Usage tracking
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;
    
    @Column(name = "max_usage_count")
    private Integer maxUsageCount;
    
    @Column(name = "cooldown_period_hours")
    private Integer cooldownPeriodHours;
    
    @Column(name = "last_cooldown_started")
    private Instant lastCooldownStarted;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    public enum AssignmentType {
        PERMANENT,
        TEMPORARY,
        JIT, // Just-In-Time
        DELEGATED,
        INHERITED,
        EMERGENCY
    }
    
    public enum RoleStatus {
        ACTIVE,
        PENDING_APPROVAL,
        SUSPENDED,
        REVOKED,
        EXPIRED,
        COOLDOWN
    }
    
    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = Instant.now();
        }
    }
    
    public boolean isActive() {
        return status == RoleStatus.ACTIVE && 
               !isExpired() && 
               !isRevoked() &&
               !isInCooldown();
    }
    
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
    
    public boolean isRevoked() {
        return status == RoleStatus.REVOKED || revokedAt != null;
    }
    
    public boolean isInCooldown() {
        if (status == RoleStatus.COOLDOWN) return true;
        
        if (cooldownPeriodHours != null && lastCooldownStarted != null) {
            Instant cooldownEnd = lastCooldownStarted.plusSeconds(cooldownPeriodHours * 3600L);
            return cooldownEnd.isAfter(Instant.now());
        }
        
        return false;
    }
    
    public boolean requiresApproval() {
        return approvalRequired && status == RoleStatus.PENDING_APPROVAL;
    }
    
    public boolean canDelegate() {
        return maxDelegationDepth > delegationDepth && 
               assignmentType != AssignmentType.DELEGATED &&
               isActive();
    }
    
    public boolean hasUsageLimit() {
        return maxUsageCount != null && usageCount >= maxUsageCount;
    }
    
    public void recordUsage() {
        this.usageCount++;
        this.lastUsedAt = Instant.now();
        
        // Start cooldown if usage limit reached
        if (hasUsageLimit() && cooldownPeriodHours != null) {
            this.status = RoleStatus.COOLDOWN;
            this.lastCooldownStarted = Instant.now();
        }
    }
    
    public void revoke(String reason, String revokedBy) {
        this.status = RoleStatus.REVOKED;
        this.revokedAt = Instant.now();
        this.revocationReason = reason;
        this.revokedByUserId = revokedBy;
    }
    
    public void approve(String approvedBy) {
        this.status = RoleStatus.ACTIVE;
        this.approvedAt = Instant.now();
        this.approvedByUserId = approvedBy;
    }
    
    public void suspend() {
        this.status = RoleStatus.SUSPENDED;
    }
    
    public void reactivate() {
        if (status == RoleStatus.SUSPENDED) {
            this.status = RoleStatus.ACTIVE;
        }
    }
    
    public boolean isTemporary() {
        return assignmentType == AssignmentType.TEMPORARY || 
               assignmentType == AssignmentType.JIT ||
               expiresAt != null;
    }
    
    public boolean isDelegated() {
        return assignmentType == AssignmentType.DELEGATED ||
               delegatedFromUserId != null;
    }
    
    public boolean isBreakGlass() {
        return breakGlassRole || emergencyAccess;
    }
    
    public String getFullScope() {
        if (scope != null) {
            return roleName + "@" + scope;
        }
        return roleName;
    }
}
