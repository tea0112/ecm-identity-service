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
import java.time.temporal.ChronoUnit;

/**
 * Represents various credential types for users including WebAuthn, TOTP, and recovery codes.
 * Supports multiple authentication factors and secure credential management.
 */
@Entity
@Table(name = "user_credentials", indexes = {
    @Index(name = "idx_credentials_user_type", columnList = "user_id, credential_type"),
    @Index(name = "idx_credentials_identifier", columnList = "credential_identifier"),
    @Index(name = "idx_credentials_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserCredential extends BaseEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "credential_type", nullable = false)
    private CredentialType credentialType;
    
    @NotBlank
    @Column(name = "credential_identifier", nullable = false, length = 500)
    private String credentialIdentifier;
    
    @Column(name = "credential_data", columnDefinition = "text")
    private String credentialData;
    
    @Column(name = "credential_name", length = 200)
    private String credentialName;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CredentialStatus status = CredentialStatus.ACTIVE;
    
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    
    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usageCount = 0;
    
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    @Column(name = "created_by_device_id")
    private String createdByDeviceId;
    
    @Column(name = "backup_eligible", nullable = false)
    @Builder.Default
    private Boolean backupEligible = false;
    
    @Column(name = "backup_state", length = 50)
    private String backupState;
    
    // WebAuthn specific fields
    @Column(name = "webauthn_credential_id", length = 500)
    private String webauthnCredentialId;
    
    @Column(name = "webauthn_public_key", columnDefinition = "text")
    private String webauthnPublicKey;
    
    @Column(name = "webauthn_signature_count")
    @Builder.Default
    private Long webauthnSignatureCount = 0L;
    
    @Column(name = "webauthn_aaguid", length = 100)
    private String webauthnAaguid;
    
    @Column(name = "webauthn_attestation_type", length = 50)
    private String webauthnAttestationType;
    
    @Column(name = "webauthn_transports", columnDefinition = "text[]")
    private String[] webauthnTransports;
    
    // TOTP specific fields
    @Column(name = "totp_secret", length = 500)
    private String totpSecret;
    
    @Column(name = "totp_algorithm", length = 20)
    @Builder.Default
    private String totpAlgorithm = "SHA1";
    
    @Column(name = "totp_digits", nullable = false)
    @Builder.Default
    private Integer totpDigits = 6;
    
    @Column(name = "totp_period", nullable = false)
    @Builder.Default
    private Integer totpPeriod = 30;
    
    @Column(name = "totp_qr_code_url", length = 1000)
    private String totpQrCodeUrl;
    
    // Recovery code specific fields
    @Column(name = "recovery_code_hash", length = 255)
    private String recoveryCodeHash;
    
    @Column(name = "recovery_code_used", nullable = false)
    @Builder.Default
    private Boolean recoveryCodeUsed = false;
    
    // SMS/Email specific fields
    @Column(name = "delivery_target", length = 255)
    private String deliveryTarget;
    
    @Column(name = "verification_code", length = 20)
    private String verificationCode;
    
    @Column(name = "verification_attempts", nullable = false)
    @Builder.Default
    private Integer verificationAttempts = 0;
    
    @Column(name = "max_verification_attempts", nullable = false)
    @Builder.Default
    private Integer maxVerificationAttempts = 3;
    
    @Column(name = "blocked_until")
    private Instant blockedUntil;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    public enum CredentialType {
        WEBAUTHN_PLATFORM,
        WEBAUTHN_ROAMING,
        TOTP,
        SMS,
        EMAIL,
        RECOVERY_CODE,
        BACKUP_CODE,
        MAGIC_LINK,
        PUSH_NOTIFICATION,
        VOICE_CALL
    }
    
    public enum CredentialStatus {
        ACTIVE,
        INACTIVE,
        EXPIRED,
        REVOKED,
        BLOCKED,
        PENDING_VERIFICATION
    }
    
    public void recordUsage() {
        this.lastUsedAt = Instant.now();
        this.usageCount++;
        
        // For WebAuthn, increment signature counter
        if (credentialType == CredentialType.WEBAUTHN_PLATFORM || 
            credentialType == CredentialType.WEBAUTHN_ROAMING) {
            this.webauthnSignatureCount++;
        }
    }
    
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
    
    public boolean isBlocked() {
        return status == CredentialStatus.BLOCKED || 
               (blockedUntil != null && blockedUntil.isAfter(Instant.now()));
    }
    
    public boolean isUsable() {
        return status == CredentialStatus.ACTIVE && 
               !isExpired() && 
               !isBlocked();
    }
    
    public void block(String reason) {
        this.status = CredentialStatus.BLOCKED;
        this.blockedUntil = Instant.now().plus(1, ChronoUnit.DAYS); // Default 1 day block
    }
    
    public void revoke() {
        this.status = CredentialStatus.REVOKED;
    }
    
    public void incrementVerificationAttempts() {
        this.verificationAttempts++;
        if (this.verificationAttempts >= this.maxVerificationAttempts) {
            this.blockedUntil = Instant.now().plus(15, ChronoUnit.MINUTES); // Block for 15 minutes
        }
    }
    
    public void resetVerificationAttempts() {
        this.verificationAttempts = 0;
        this.blockedUntil = null;
    }
    
    public boolean isWebAuthn() {
        return credentialType == CredentialType.WEBAUTHN_PLATFORM || 
               credentialType == CredentialType.WEBAUTHN_ROAMING;
    }
    
    public boolean isTOTP() {
        return credentialType == CredentialType.TOTP;
    }
    
    public boolean isRecoveryCode() {
        return credentialType == CredentialType.RECOVERY_CODE || 
               credentialType == CredentialType.BACKUP_CODE;
    }
}
