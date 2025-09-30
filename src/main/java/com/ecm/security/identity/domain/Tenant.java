package com.ecm.security.identity.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a tenant in the multi-tenant IAM system.
 * Each tenant has isolated data and configurable security policies.
 */
@Entity
@Table(name = "tenants", uniqueConstraints = {
    @UniqueConstraint(columnNames = "tenant_code"),
    @UniqueConstraint(columnNames = "domain")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {
    
    @NotBlank
    @Column(name = "tenant_code", nullable = false, length = 50)
    private String tenantCode;
    
    @NotBlank
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Column(name = "domain", length = 255)
    private String domain;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;
    
    @Column(name = "subscription_tier", length = 50)
    private String subscriptionTier;
    
    @Column(name = "max_users")
    private Integer maxUsers;
    
    @Column(name = "settings", columnDefinition = "text")
    private String settings;
    
    @Column(name = "crypto_key_id", length = 255)
    private String cryptoKeyId;
    
    @Column(name = "backup_rpo_minutes")
    @Builder.Default
    private Integer backupRpoMinutes = 5;
    
    @Column(name = "backup_rto_minutes")
    @Builder.Default
    private Integer backupRtoMinutes = 60;
    
    @Column(name = "suspended_at")
    private Instant suspendedAt;
    
    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;
    
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<User> users = new HashSet<>();
    
    @OneToMany(mappedBy = "tenant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<TenantPolicy> policies = new HashSet<>();
    
    public enum TenantStatus {
        ACTIVE,
        SUSPENDED,
        PENDING_DELETION,
        ARCHIVED
    }
    
    public boolean isActive() {
        return status == TenantStatus.ACTIVE && suspendedAt == null;
    }
    
    public void suspend(String reason) {
        this.status = TenantStatus.SUSPENDED;
        this.suspendedAt = Instant.now();
        this.suspensionReason = reason;
    }
    
    public void reactivate() {
        this.status = TenantStatus.ACTIVE;
        this.suspendedAt = null;
        this.suspensionReason = null;
    }
}
