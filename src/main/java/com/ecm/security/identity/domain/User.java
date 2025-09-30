package com.ecm.security.identity.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a user in the IAM system with support for multi-tenancy,
 * multiple authentication methods, and comprehensive security features.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "email"}),
    @UniqueConstraint(columnNames = {"tenant_id", "username"})
}, indexes = {
    @Index(name = "idx_users_tenant_email", columnList = "tenant_id, email"),
    @Index(name = "idx_users_tenant_username", columnList = "tenant_id, username"),
    @Index(name = "idx_users_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    @Column(name = "username", length = 100)
    private String username;
    
    @NotBlank
    @Email
    @Column(name = "email", nullable = false, length = 255)
    private String email;
    
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;
    
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;
    
    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private Boolean phoneVerified = false;
    
    @Column(name = "password_hash", length = 255)
    private String passwordHash;
    
    @Column(name = "password_salt", length = 255)
    private String passwordSalt;
    
    @Column(name = "password_algorithm", length = 50)
    @Builder.Default
    private String passwordAlgorithm = "ARGON2";
    
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;
    
    @Column(name = "password_expires_at")
    private Instant passwordExpiresAt;
    
    @Column(name = "first_name", length = 100)
    private String firstName;
    
    @Column(name = "last_name", length = 100)
    private String lastName;
    
    @Column(name = "display_name", length = 200)
    private String displayName;
    
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;
    
    @Column(name = "profile_picture_url", length = 500)
    private String profilePictureUrl;
    
    @Column(name = "locale", length = 10)
    @Builder.Default
    private String locale = "en_US";
    
    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "UTC";
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING_VERIFICATION;
    
    @Column(name = "mfa_enabled", nullable = false)
    @Builder.Default
    private Boolean mfaEnabled = false;
    
    @Column(name = "mfa_backup_codes", columnDefinition = "clob")
    private String mfaBackupCodes;
    
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;
    
    @Column(name = "locked_until")
    private Instant lockedUntil;
    
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    
    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;
    
    @Column(name = "last_activity_at")
    private Instant lastActivityAt;
    
    @Column(name = "terms_accepted_at")
    private Instant termsAcceptedAt;
    
    @Column(name = "terms_version", length = 50)
    private String termsVersion;
    
    @Column(name = "privacy_policy_accepted_at")
    private Instant privacyPolicyAcceptedAt;
    
    @Column(name = "privacy_policy_version", length = 50)
    private String privacyPolicyVersion;
    
    @Column(name = "marketing_consent", nullable = false)
    @Builder.Default
    private Boolean marketingConsent = false;
    
    @Column(name = "is_minor", nullable = false)
    @Builder.Default
    private Boolean isMinor = false;
    
    @Column(name = "parental_consent_at")
    private Instant parentalConsentAt;
    
    @Column(name = "parent_email", length = 255)
    private String parentEmail;
    
    @Column(name = "metadata", columnDefinition = "clob")
    private String metadata;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserSession> sessions = new HashSet<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserDevice> devices = new HashSet<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserCredential> credentials = new HashSet<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<UserRole> roles = new HashSet<>();
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<LinkedIdentity> linkedIdentities = new HashSet<>();
    
    public enum UserStatus {
        PENDING_VERIFICATION,
        ACTIVE,
        SUSPENDED,
        LOCKED,
        DEACTIVATED,
        PENDING_DELETION
    }
    
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (displayName != null) {
            return displayName;
        } else {
            return email;
        }
    }
    
    public boolean isActive() {
        return status == UserStatus.ACTIVE && 
               (lockedUntil == null || lockedUntil.isBefore(Instant.now()));
    }
    
    public boolean isLocked() {
        return status == UserStatus.LOCKED || 
               (lockedUntil != null && lockedUntil.isAfter(Instant.now()));
    }
    
    public void lockAccount(int lockoutDurationMinutes) {
        this.status = UserStatus.LOCKED;
        this.lockedUntil = Instant.now().plusSeconds(lockoutDurationMinutes * 60L);
    }
    
    public void unlockAccount() {
        this.status = UserStatus.ACTIVE;
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
    }
    
    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
    }
    
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
    }
    
    public void recordSuccessfulLogin(String ipAddress) {
        this.lastLoginAt = Instant.now();
        this.lastLoginIp = ipAddress;
        this.lastActivityAt = Instant.now();
        this.resetFailedLoginAttempts();
    }
    
    public boolean requiresParentalConsent() {
        return isMinor && parentalConsentAt == null;
    }
}
