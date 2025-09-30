package com.ecm.security.identity.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit event with cryptographic chaining for tamper-evident logging.
 * Supports field-level PII redaction and forensic timeline construction.
 */
@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_tenant_user", columnList = "tenant_id, user_id"),
    @Index(name = "idx_audit_event_type", columnList = "event_type"),
    @Index(name = "idx_audit_session", columnList = "session_id"),
    @Index(name = "idx_audit_severity", columnList = "severity"),
    @Index(name = "idx_audit_chain", columnList = "previous_event_hash")
})
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    
    @NotNull
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;
    
    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;
    
    @Column(name = "user_id", updatable = false)
    private UUID userId;
    
    @Column(name = "session_id", updatable = false, length = 255)
    private String sessionId;
    
    @NotBlank
    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;
    
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, updatable = false)
    private Severity severity;
    
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;
    
    @Column(name = "actor_type", updatable = false, length = 50)
    private String actorType; // USER, ADMIN, SYSTEM, SERVICE
    
    @Column(name = "target_id", updatable = false)
    private UUID targetId;
    
    @Column(name = "target_type", updatable = false, length = 50)
    private String targetType;
    
    @Column(name = "resource", updatable = false, length = 255)
    private String resource;
    
    @Column(name = "action", updatable = false, length = 100)
    private String action;
    
    @Column(name = "outcome", updatable = false, length = 20)
    private String outcome; // SUCCESS, FAILURE, DENIED
    
    @Column(name = "ip_address", updatable = false, length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", updatable = false, length = 1000)
    private String userAgent;
    
    @Column(name = "location_country", updatable = false, length = 2)
    private String locationCountry;
    
    @Column(name = "location_city", updatable = false, length = 100)
    private String locationCity;
    
    @Column(name = "description", updatable = false, length = 1000)
    private String description;
    
    @Column(name = "details", updatable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String details;
    
    @Column(name = "risk_score", updatable = false, columnDefinition = "DECIMAL(5,2)")
    private Double riskScore;
    
    @Column(name = "risk_factors", updatable = false, columnDefinition = "text[]")
    private String[] riskFactors;
    
    /**
     * Add a risk factor to this audit event.
     */
    public void addRiskFactor(String riskFactor) {
        if (this.riskFactors == null) {
            this.riskFactors = new String[]{riskFactor};
        } else {
            String[] newFactors = new String[this.riskFactors.length + 1];
            System.arraycopy(this.riskFactors, 0, newFactors, 0, this.riskFactors.length);
            newFactors[this.riskFactors.length] = riskFactor;
            this.riskFactors = newFactors;
        }
    }
    
    @Column(name = "compliance_flags", updatable = false, columnDefinition = "text[]")
    private String[] complianceFlags;
    
    @Column(name = "retention_until", updatable = false)
    private Instant retentionUntil;
    
    @Column(name = "legal_hold", nullable = false, updatable = false)
    private Boolean legalHold = false;
    
    @Column(name = "pii_redacted", nullable = false, updatable = false)
    private Boolean piiRedacted = false;
    
    @Column(name = "redacted_fields", updatable = false, columnDefinition = "text[]")
    private String[] redactedFields;
    
    // Cryptographic chaining fields
    @Column(name = "event_hash", updatable = false, length = 64)
    private String eventHash;
    
    @Column(name = "previous_event_hash", updatable = false, length = 64)
    private String previousEventHash;
    
    @Column(name = "chain_sequence", updatable = false)
    private Long chainSequence;
    
    @Column(name = "signature", updatable = false, length = 500)
    private String signature;
    
    @Column(name = "signing_key_id", updatable = false, length = 100)
    private String signingKeyId;
    
    // Additional metadata
    @Column(name = "correlation_id", updatable = false, length = 255)
    private String correlationId;
    
    @Column(name = "trace_id", updatable = false, length = 255)
    private String traceId;
    
    @Column(name = "span_id", updatable = false, length = 255)
    private String spanId;
    
    @Column(name = "application_version", updatable = false, length = 50)
    private String applicationVersion;
    
    @Column(name = "environment", updatable = false, length = 20)
    private String environment;
    
    public enum Severity {
        DEBUG,
        INFO,
        WARN,
        ERROR,
        CRITICAL,
        SECURITY_INCIDENT
    }
    
    // Predefined event types for common IAM operations
    public static class EventTypes {
        // Authentication Events
        public static final String LOGIN_SUCCESS = "auth.login.success";
        public static final String LOGIN_FAILURE = "auth.login.failure";
        public static final String LOGOUT = "auth.logout";
        public static final String PASSWORD_CHANGE = "auth.password.change";
        public static final String PASSWORD_RESET = "auth.password.reset";
        public static final String MFA_ENROLLMENT = "auth.mfa.enrollment";
        public static final String MFA_CHALLENGE = "auth.mfa.challenge";
        public static final String MFA_VERIFICATION = "auth.mfa.verification";
        public static final String WEBAUTHN_REGISTRATION = "auth.webauthn.registration";
        public static final String WEBAUTHN_AUTHENTICATION = "auth.webauthn.authentication";
        
        // Session Events
        public static final String SESSION_CREATED = "session.created";
        public static final String SESSION_EXTENDED = "session.extended";
        public static final String SESSION_TERMINATED = "session.terminated";
        public static final String SESSION_HIJACK_DETECTED = "session.hijack.detected";
        public static final String IMPOSSIBLE_TRAVEL = "session.impossible.travel";
        
        // Authorization Events
        public static final String ACCESS_GRANTED = "authz.access.granted";
        public static final String ACCESS_DENIED = "authz.access.denied";
        public static final String PERMISSION_GRANTED = "authz.permission.granted";
        public static final String PERMISSION_REVOKED = "authz.permission.revoked";
        public static final String ROLE_ASSIGNED = "authz.role.assigned";
        public static final String ROLE_REMOVED = "authz.role.removed";
        public static final String POLICY_VIOLATION = "authz.policy.violation";
        
        // User Management Events
        public static final String USER_CREATED = "user.created";
        public static final String USER_UPDATED = "user.updated";
        public static final String USER_DELETED = "user.deleted";
        public static final String USER_SUSPENDED = "user.suspended";
        public static final String USER_REACTIVATED = "user.reactivated";
        public static final String USER_LOCKED = "user.locked";
        public static final String USER_UNLOCKED = "user.unlocked";
        
        // Admin Events
        public static final String ADMIN_IMPERSONATION_START = "admin.impersonation.start";
        public static final String ADMIN_IMPERSONATION_END = "admin.impersonation.end";
        public static final String BREAK_GLASS_ACCESS = "admin.break.glass.access";
        public static final String EMERGENCY_ACCESS = "admin.emergency.access";
        
        // Security Events
        public static final String SUSPICIOUS_ACTIVITY = "security.suspicious.activity";
        public static final String ACCOUNT_TAKEOVER_ATTEMPT = "security.account.takeover.attempt";
        public static final String CREDENTIAL_STUFFING = "security.credential.stuffing";
        public static final String BRUTE_FORCE_ATTACK = "security.brute.force.attack";
        public static final String ANOMALOUS_BEHAVIOR = "security.anomalous.behavior";
        
        // Compliance Events
        public static final String DATA_EXPORT = "compliance.data.export";
        public static final String DATA_DELETION = "compliance.data.deletion";
        public static final String CONSENT_GIVEN = "compliance.consent.given";
        public static final String CONSENT_WITHDRAWN = "compliance.consent.withdrawn";
        public static final String LEGAL_HOLD_APPLIED = "compliance.legal.hold.applied";
        public static final String LEGAL_HOLD_RELEASED = "compliance.legal.hold.released";
        
        // System Events
        public static final String KEY_ROTATION = "system.key.rotation";
        public static final String BACKUP_CREATED = "system.backup.created";
        public static final String BACKUP_RESTORED = "system.backup.restored";
        public static final String CONFIGURATION_CHANGED = "system.configuration.changed";
    }
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
    
    public boolean isSecurityEvent() {
        return severity == Severity.SECURITY_INCIDENT || 
               eventType.startsWith("security.") ||
               outcome.equals("DENIED");
    }
    
    public boolean requiresRetention() {
        return legalHold || retentionUntil.isAfter(Instant.now());
    }
    
    public boolean isHighSeverity() {
        return severity == Severity.ERROR || 
               severity == Severity.CRITICAL || 
               severity == Severity.SECURITY_INCIDENT;
    }
}
