package com.ecm.security.identity.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Represents linked external identities for account linking and federation.
 * Supports reversible merges and comprehensive audit trails.
 */
@Entity
@Table(name = "linked_identities", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider", "external_id"})
}, indexes = {
    @Index(name = "idx_linked_identities_user", columnList = "user_id"),
    @Index(name = "idx_linked_identities_provider", columnList = "provider"),
    @Index(name = "idx_linked_identities_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class LinkedIdentity extends BaseEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotBlank
    @Column(name = "provider", nullable = false, length = 100)
    private String provider; // google, facebook, saml, oidc, etc.
    
    @NotBlank
    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;
    
    @Column(name = "external_username", length = 255)
    private String externalUsername;
    
    @Column(name = "external_email", length = 255)
    private String externalEmail;
    
    @Column(name = "display_name", length = 200)
    private String displayName;
    
    @Column(name = "profile_url", length = 500)
    private String profileUrl;
    
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "identity_type", nullable = false)
    private IdentityType identityType;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LinkStatus status = LinkStatus.ACTIVE;
    
    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;
    
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    
    @Column(name = "login_count", nullable = false)
    private Integer loginCount = 0;
    
    @Column(name = "access_token_hash", length = 255)
    private String accessTokenHash;
    
    @Column(name = "refresh_token_hash", length = 255)
    private String refreshTokenHash;
    
    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;
    
    @Column(name = "id_token_hash", length = 255)
    private String idTokenHash;
    
    @Column(name = "scopes", columnDefinition = "text[]")
    private String[] scopes;
    
    @Column(name = "claims", columnDefinition = "jsonb")
    private String claims;
    
    @Column(name = "verified", nullable = false)
    private Boolean verified = false;
    
    @Column(name = "verification_method", length = 50)
    private String verificationMethod;
    
    @Column(name = "verification_at")
    private Instant verificationAt;
    
    // Account merge support
    @Column(name = "merge_eligible", nullable = false)
    private Boolean mergeEligible = true;
    
    @Column(name = "merged_from_user_id")
    private String mergedFromUserId;
    
    @Column(name = "merge_operation_id", length = 255)
    private String mergeOperationId;
    
    @Column(name = "merge_reversible", nullable = false)
    private Boolean mergeReversible = true;
    
    @Column(name = "merge_expires_at")
    private Instant mergeExpiresAt;
    
    // Unlinking support
    @Column(name = "unlinked_at")
    private Instant unlinkedAt;
    
    @Column(name = "unlinked_by_user_id")
    private String unlinkedByUserId;
    
    @Column(name = "unlink_reason", length = 500)
    private String unlinkReason;
    
    @Column(name = "can_relink", nullable = false)
    private Boolean canRelink = true;
    
    // Privacy and consent
    @Column(name = "data_sharing_consent", nullable = false)
    private Boolean dataSharingConsent = false;
    
    @Column(name = "consent_given_at")
    private Instant consentGivenAt;
    
    @Column(name = "consent_withdrawn_at")
    private Instant consentWithdrawnAt;
    
    @Column(name = "profile_sync_enabled", nullable = false)
    private Boolean profileSyncEnabled = false;
    
    @Column(name = "last_profile_sync")
    private Instant lastProfileSync;
    
    // Risk and security
    @Column(name = "risk_score")
    private Double riskScore = 0.0;
    
    @Column(name = "suspicious_activity", nullable = false)
    private Boolean suspiciousActivity = false;
    
    @Column(name = "fraud_indicators", columnDefinition = "text[]")
    private String[] fraudIndicators;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    public enum IdentityType {
        SOCIAL,
        ENTERPRISE_SAML,
        ENTERPRISE_OIDC,
        OAUTH2,
        CUSTOM
    }
    
    public enum LinkStatus {
        ACTIVE,
        SUSPENDED,
        UNLINKED,
        PENDING_VERIFICATION,
        EXPIRED,
        MERGED
    }
    
    @PrePersist
    protected void onCreate() {
        if (linkedAt == null) {
            linkedAt = Instant.now();
        }
    }
    
    public boolean isActive() {
        return status == LinkStatus.ACTIVE && !isTokenExpired();
    }
    
    public boolean isTokenExpired() {
        return tokenExpiresAt != null && tokenExpiresAt.isBefore(Instant.now());
    }
    
    public boolean canUnlink() {
        return status == LinkStatus.ACTIVE && 
               unlinkedAt == null &&
               !suspiciousActivity;
    }
    
    public boolean canMerge() {
        return mergeEligible && 
               status == LinkStatus.ACTIVE &&
               verified;
    }
    
    public boolean isMerged() {
        return status == LinkStatus.MERGED || mergedFromUserId != null;
    }
    
    public boolean canReverseMerge() {
        return isMerged() && 
               mergeReversible &&
               (mergeExpiresAt == null || mergeExpiresAt.isAfter(Instant.now()));
    }
    
    public void recordLogin() {
        this.loginCount++;
        this.lastLoginAt = Instant.now();
    }
    
    public void unlink(String reason, String unlinkedBy) {
        this.status = LinkStatus.UNLINKED;
        this.unlinkedAt = Instant.now();
        this.unlinkReason = reason;
        this.unlinkedByUserId = unlinkedBy;
        
        // Clear sensitive data
        this.accessTokenHash = null;
        this.refreshTokenHash = null;
        this.idTokenHash = null;
    }
    
    public void suspend() {
        this.status = LinkStatus.SUSPENDED;
    }
    
    public void reactivate() {
        if (status == LinkStatus.SUSPENDED) {
            this.status = LinkStatus.ACTIVE;
        }
    }
    
    public void giveConsent() {
        this.dataSharingConsent = true;
        this.consentGivenAt = Instant.now();
        this.consentWithdrawnAt = null;
    }
    
    public void withdrawConsent() {
        this.dataSharingConsent = false;
        this.consentWithdrawnAt = Instant.now();
        this.profileSyncEnabled = false;
    }
    
    public void markAsSuspicious(String[] indicators) {
        this.suspiciousActivity = true;
        this.fraudIndicators = indicators;
        this.riskScore = Math.min(100.0, this.riskScore + 25.0);
    }
    
    public void clearSuspiciousActivity() {
        this.suspiciousActivity = false;
        this.fraudIndicators = null;
        this.riskScore = Math.max(0.0, this.riskScore - 10.0);
    }
    
    public boolean requiresVerification() {
        return status == LinkStatus.PENDING_VERIFICATION || !verified;
    }
    
    public void verify(String method) {
        this.verified = true;
        this.verificationMethod = method;
        this.verificationAt = Instant.now();
        if (status == LinkStatus.PENDING_VERIFICATION) {
            this.status = LinkStatus.ACTIVE;
        }
    }
    
    public String getProviderDisplayName() {
        return switch (provider.toLowerCase()) {
            case "google" -> "Google";
            case "facebook" -> "Facebook";
            case "github" -> "GitHub";
            case "linkedin" -> "LinkedIn";
            case "microsoft" -> "Microsoft";
            case "apple" -> "Apple";
            case "saml" -> "Enterprise SSO";
            case "oidc" -> "OpenID Connect";
            default -> provider;
        };
    }
}
