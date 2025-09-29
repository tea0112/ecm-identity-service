package com.ecm.security.identity.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Tracks detailed session activities for security monitoring and forensic analysis.
 * Enables construction of user activity timelines and anomaly detection.
 */
@Entity
@Table(name = "session_activities", indexes = {
    @Index(name = "idx_session_activities_session", columnList = "session_id"),
    @Index(name = "idx_session_activities_timestamp", columnList = "timestamp"),
    @Index(name = "idx_session_activities_activity_type", columnList = "activity_type"),
    @Index(name = "idx_session_activities_risk", columnList = "risk_score")
})
@Getter
@Setter
@NoArgsConstructor
public class SessionActivity extends BaseEntity {
    
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private UserSession session;
    
    @NotNull
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;
    
    @NotBlank
    @Column(name = "activity_type", nullable = false, length = 100)
    private String activityType;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Column(name = "resource", length = 255)
    private String resource;
    
    @Column(name = "action", length = 100)
    private String action;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", length = 1000)
    private String userAgent;
    
    @Column(name = "location_changed", nullable = false)
    private Boolean locationChanged = false;
    
    @Column(name = "device_fingerprint_changed", nullable = false)
    private Boolean deviceFingerprintChanged = false;
    
    @Column(name = "risk_score")
    private Double riskScore = 0.0;
    
    @Column(name = "risk_factors", columnDefinition = "text[]")
    private String[] riskFactors;
    
    @Column(name = "anomaly_detected", nullable = false)
    private Boolean anomalyDetected = false;
    
    @Column(name = "anomaly_type", length = 100)
    private String anomalyType;
    
    @Column(name = "response_time_ms")
    private Long responseTimeMs;
    
    @Column(name = "bytes_transferred")
    private Long bytesTransferred;
    
    @Column(name = "status_code")
    private Integer statusCode;
    
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    
    @Column(name = "additional_data", columnDefinition = "jsonb")
    private String additionalData;
    
    // Predefined activity types
    public static class ActivityTypes {
        public static final String PAGE_VIEW = "page.view";
        public static final String API_CALL = "api.call";
        public static final String RESOURCE_ACCESS = "resource.access";
        public static final String DATA_EXPORT = "data.export";
        public static final String SETTINGS_CHANGE = "settings.change";
        public static final String PASSWORD_CHANGE = "password.change";
        public static final String MFA_SETUP = "mfa.setup";
        public static final String ROLE_ASSIGNMENT = "role.assignment";
        public static final String PERMISSION_GRANT = "permission.grant";
        public static final String FILE_UPLOAD = "file.upload";
        public static final String FILE_DOWNLOAD = "file.download";
        public static final String SEARCH_QUERY = "search.query";
        public static final String ADMIN_ACTION = "admin.action";
        public static final String BREAK_GLASS = "break.glass";
        public static final String IMPERSONATION = "impersonation";
        public static final String SESSION_EXTEND = "session.extend";
        public static final String STEP_UP_AUTH = "step.up.auth";
        public static final String CONSENT_GRANT = "consent.grant";
        public static final String CONSENT_REVOKE = "consent.revoke";
        public static final String DEVICE_REGISTRATION = "device.registration";
        public static final String CREDENTIAL_UPDATE = "credential.update";
        public static final String SUSPICIOUS_ACTIVITY = "suspicious.activity";
        public static final String IMPOSSIBLE_TRAVEL = "impossible.travel";
        public static final String CONCURRENT_SESSION = "concurrent.session";
        public static final String RATE_LIMIT_HIT = "rate.limit.hit";
        public static final String GEOFENCE_VIOLATION = "geofence.violation";
    }
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
    
    public boolean isHighRisk() {
        return riskScore != null && riskScore > 70.0;
    }
    
    public boolean isAnomalous() {
        return anomalyDetected || isHighRisk();
    }
    
    public boolean isSecurityEvent() {
        return activityType.contains("suspicious") ||
               activityType.contains("impossible") ||
               activityType.contains("break.glass") ||
               activityType.contains("geofence") ||
               anomalyDetected;
    }
    
    public boolean isAdminActivity() {
        return activityType.startsWith("admin.") ||
               activityType.equals(ActivityTypes.IMPERSONATION) ||
               activityType.equals(ActivityTypes.BREAK_GLASS);
    }
    
    public boolean isDataAccess() {
        return activityType.equals(ActivityTypes.DATA_EXPORT) ||
               activityType.equals(ActivityTypes.FILE_DOWNLOAD) ||
               activityType.equals(ActivityTypes.RESOURCE_ACCESS);
    }
    
    public boolean isAuthenticationRelated() {
        return activityType.contains("auth") ||
               activityType.contains("mfa") ||
               activityType.contains("password") ||
               activityType.contains("credential");
    }
    
    public void addRiskFactor(String factor) {
        if (riskFactors == null) {
            riskFactors = new String[]{factor};
        } else {
            String[] newFactors = new String[riskFactors.length + 1];
            System.arraycopy(riskFactors, 0, newFactors, 0, riskFactors.length);
            newFactors[riskFactors.length] = factor;
            riskFactors = newFactors;
        }
    }
    
    public void markAsAnomaly(String type) {
        this.anomalyDetected = true;
        this.anomalyType = type;
        if (this.riskScore == null || this.riskScore < 50.0) {
            this.riskScore = 75.0;
        }
    }
    
    public boolean hasLocationChange() {
        return locationChanged;
    }
    
    public boolean hasDeviceChange() {
        return deviceFingerprintChanged;
    }
    
    public boolean isErrorActivity() {
        return statusCode != null && statusCode >= 400;
    }
    
    public boolean isSlowActivity() {
        return responseTimeMs != null && responseTimeMs > 5000; // 5 seconds threshold
    }
    
    public boolean isLargeDataTransfer() {
        return bytesTransferred != null && bytesTransferred > 100_000_000; // 100MB threshold
    }
}
