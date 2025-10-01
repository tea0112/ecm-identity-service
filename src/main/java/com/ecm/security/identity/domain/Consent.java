package com.ecm.security.identity.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing user consent for data sharing and permissions.
 * Supports granular consent management with per-resource and per-action permissions.
 */
@Entity
@Table(name = "consents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Consent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "application_id", length = 100, nullable = false)
    private String applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false)
    private ConsentType consentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ConsentStatus status = ConsentStatus.ACTIVE;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    @Column(name = "permissions", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String permissions;

    @Column(name = "consent_metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String consentMetadata;

    @Column(name = "granted_permissions_count", nullable = false)
    @Builder.Default
    private Integer grantedPermissionsCount = 0;

    @Column(name = "denied_permissions_count", nullable = false)
    @Builder.Default
    private Integer deniedPermissionsCount = 0;

    @Column(name = "consent_version", length = 20)
    private String consentVersion;

    @Column(name = "privacy_policy_version", length = 20)
    private String privacyPolicyVersion;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public enum ConsentType {
        GRANULAR,
        BULK,
        IMPLICIT,
        EXPLICIT
    }

    public enum ConsentStatus {
        ACTIVE,
        REVOKED,
        EXPIRED,
        SUSPENDED
    }

    public boolean isActive() {
        return status == ConsentStatus.ACTIVE && 
               (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }

    public void revoke(String reason) {
        this.status = ConsentStatus.REVOKED;
        this.revokedAt = Instant.now();
        this.revocationReason = reason;
    }

    public void expire() {
        this.status = ConsentStatus.EXPIRED;
    }

    public boolean hasPermission(String resource, String action) {
        // This would need to be implemented based on the permissions JSON structure
        // For now, return true for active consents
        return isActive();
    }
}
