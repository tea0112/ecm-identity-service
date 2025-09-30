package com.ecm.security.identity.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Represents a user's device with comprehensive fingerprinting and trust scoring.
 * Supports device attestation and integrity verification.
 */
@Entity
@Table(name = "user_devices", indexes = {
    @Index(name = "idx_devices_user_trusted", columnList = "user_id, is_trusted"),
    @Index(name = "idx_devices_fingerprint", columnList = "device_fingerprint"),
    @Index(name = "idx_devices_last_seen", columnList = "last_seen_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDevice extends BaseEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotBlank
    @Column(name = "device_fingerprint", nullable = false, length = 500)
    private String deviceFingerprint;
    
    @Column(name = "device_name", length = 200)
    private String deviceName;
    
    @Column(name = "device_type", length = 50)
    private String deviceType;
    
    @Column(name = "operating_system", length = 100)
    private String operatingSystem;
    
    @Column(name = "browser", length = 100)
    private String browser;
    
    @Column(name = "browser_version", length = 50)
    private String browserVersion;
    
    @Column(name = "platform", length = 50)
    private String platform;
    
    @Column(name = "screen_resolution", length = 20)
    private String screenResolution;
    
    @Column(name = "timezone", length = 50)
    private String timezone;
    
    @Column(name = "language", length = 10)
    private String language;
    
    @Column(name = "user_agent", length = 1000)
    private String userAgent;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "is_trusted", nullable = false)
    @Builder.Default
    private Boolean isTrusted = false;

    @Column(name = "trust_score")
    @Builder.Default
    private Double trustScore = 0.0;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "total_sessions", nullable = false)
    @Builder.Default
    private Integer totalSessions = 0;

    @Column(name = "successful_authentications", nullable = false)
    @Builder.Default
    private Integer successfulAuthentications = 0;

    @Column(name = "failed_authentications", nullable = false)
    @Builder.Default
    private Integer failedAuthentications = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DeviceStatus status = DeviceStatus.UNVERIFIED;

    @Column(name = "attestation_supported", nullable = false)
    @Builder.Default
    private Boolean attestationSupported = false;

    @Column(name = "attestation_verified", nullable = false)
    @Builder.Default
    private Boolean attestationVerified = false;

    @Column(name = "attestation_data", columnDefinition = "text")
    private String attestationData;

    @Column(name = "tpm_present", nullable = false)
    @Builder.Default
    private Boolean tpmPresent = false;

    @Column(name = "secure_element_present", nullable = false)
    @Builder.Default
    private Boolean secureElementPresent = false;

    @Column(name = "jailbroken_rooted", nullable = false)
    @Builder.Default
    private Boolean jailbrokenRooted = false;

    @Column(name = "emulator_detected", nullable = false)
    @Builder.Default
    private Boolean emulatorDetected = false;

    @Column(name = "vpn_detected", nullable = false)
    @Builder.Default
    private Boolean vpnDetected = false;

    @Column(name = "tor_detected", nullable = false)
    @Builder.Default
    private Boolean torDetected = false;

    @Column(name = "geolocation_country", length = 2)
    private String geolocationCountry;

    @Column(name = "geolocation_city", length = 100)
    private String geolocationCity;

    @Column(name = "push_token", length = 500)
    private String pushToken;

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private Boolean pushEnabled = false;
    
    @Column(name = "blocked_at")
    private Instant blockedAt;
    
    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;
    
    @Column(name = "device_metadata")
    @JdbcTypeCode(SqlTypes.JSON)
    private String deviceMetadata;
    
    public enum DeviceStatus {
        UNVERIFIED,
        VERIFIED,
        TRUSTED,
        BLOCKED,
        COMPROMISED
    }
    
    public void recordAuthentication(boolean successful) {
        if (successful) {
            this.successfulAuthentications++;
        } else {
            this.failedAuthentications++;
        }
        this.lastSeenAt = Instant.now();
        updateTrustScore();
    }
    
    public void recordSession() {
        this.totalSessions++;
        this.lastSeenAt = Instant.now();
        if (this.firstSeenAt == null) {
            this.firstSeenAt = Instant.now();
        }
    }
    
    public void updateTrustScore() {
        double score = 0.0;
        
        // Base score from successful authentications
        if (totalSessions > 0) {
            double successRate = (double) successfulAuthentications / (successfulAuthentications + failedAuthentications);
            score += successRate * 40;
        }
        
        // Age factor
        if (firstSeenAt != null) {
            long daysOld = java.time.Duration.between(firstSeenAt, Instant.now()).toDays();
            score += Math.min(daysOld * 2, 30); // Max 30 points for age
        }
        
        // Security features
        if (attestationVerified) score += 20;
        if (tpmPresent) score += 10;
        if (secureElementPresent) score += 10;
        
        // Risk factors
        if (jailbrokenRooted) score -= 30;
        if (emulatorDetected) score -= 40;
        if (vpnDetected) score -= 10;
        if (torDetected) score -= 20;
        
        this.trustScore = Math.max(0, Math.min(100, score));
        
        // Update trust status
        if (this.trustScore >= 80) {
            this.isTrusted = true;
            this.status = DeviceStatus.TRUSTED;
        } else if (this.trustScore >= 60) {
            this.status = DeviceStatus.VERIFIED;
        }
    }
    
    public void blockDevice(String reason) {
        this.status = DeviceStatus.BLOCKED;
        this.blockedAt = Instant.now();
        this.blockedReason = reason;
        this.isTrusted = false;
    }
    
    public void markAsCompromised() {
        this.status = DeviceStatus.COMPROMISED;
        this.isTrusted = false;
        this.trustScore = 0.0;
    }
    
    public boolean isBlocked() {
        return status == DeviceStatus.BLOCKED || status == DeviceStatus.COMPROMISED;
    }
    
    public boolean hasSecurityConcerns() {
        return jailbrokenRooted || emulatorDetected || torDetected || isBlocked();
    }
}
