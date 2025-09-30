package com.ecm.security.identity.service;

import com.ecm.security.identity.domain.AuditEvent;
import com.ecm.security.identity.domain.TenantPolicy;
import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.repository.AuditEventRepository;
import com.ecm.security.identity.repository.UserRepository;
import com.ecm.security.identity.service.AuthorizationService.AuthorizationDecision;
import com.ecm.security.identity.service.AuthorizationService.AuthorizationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for comprehensive audit logging with cryptographic integrity.
 * Implements immutable audit trails with tamper-evident chaining.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {
    
    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final TenantContextService tenantContextService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong sequenceCounter = new AtomicLong(0);
    
    /**
     * Logs an authorization decision for audit purposes.
     */
    @Async
    @Transactional
    public void logAuthorizationDecision(AuthorizationRequest request, AuthorizationDecision decision) {
        try {
            AuditEvent event = createBaseEvent(AuditEvent.EventTypes.ACCESS_GRANTED);
            
            if (decision.getDecision() == AuthorizationService.Decision.DENY) {
                event.setEventType(AuditEvent.EventTypes.ACCESS_DENIED);
                event.setSeverity(AuditEvent.Severity.WARN);
            }
            
            event.setTenantId(request.getTenantId());
            event.setResource(request.getResource());
            event.setAction(request.getAction());
            event.setOutcome(decision.getDecision().toString());
            event.setDescription(String.format("Authorization %s for %s on %s:%s", 
                decision.getDecision().toString().toLowerCase(),
                request.getSubject(),
                request.getResource(),
                request.getAction()));
            
            // Add decision details
            event.setDetails(createAuthorizationDetailsJson(request, decision));
            
            saveAuditEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to log authorization decision", e);
        }
    }
    
    /**
     * Logs a policy management event.
     */
    @Async
    @Transactional
    public void logPolicyEvent(String eventType, TenantPolicy policy, TenantPolicy originalPolicy) {
        try {
            AuditEvent event = createBaseEvent("policy." + eventType.toLowerCase());
            event.setTenantId(policy.getTenant().getId());
            event.setTargetId(policy.getId());
            event.setTargetType("POLICY");
            event.setResource("tenant_policy");
            event.setAction(eventType.toLowerCase());
            event.setOutcome("SUCCESS");
            
            event.setDescription(String.format("Policy %s: %s", 
                eventType.toLowerCase().replace("_", " "), 
                policy.getName()));
            
            // Add policy details
            event.setDetails(createPolicyDetailsJson(policy, originalPolicy));
            
            saveAuditEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to log policy event", e);
        }
    }
    
    /**
     * Logs a user authentication event.
     */
    @Async
    @Transactional
    public void logAuthenticationEvent(String eventType, String username, boolean success, String reason) {
        log.debug("logAuthenticationEvent called with eventType: {}, username: {}, success: {}", eventType, username, success);
        try {
            AuditEvent event = createBaseEvent(eventType);
            event.setActorType("USER");
            event.setResource("authentication");
            event.setAction("authenticate");
            event.setOutcome(success ? "SUCCESS" : "FAILURE");
            event.setSeverity(success ? AuditEvent.Severity.INFO : AuditEvent.Severity.WARN);
            
            event.setDescription(String.format("Authentication %s for user %s%s",
                success ? "succeeded" : "failed",
                username,
                reason != null ? ": " + reason : ""));
            
            // Add authentication details
            event.setDetails(createAuthenticationDetailsJson(username, success, reason));
            
            // Set userId if user exists
            try {
                Optional<User> userOpt = userRepository.findByEmail(username);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    event.setUserId(user.getId());
                    log.debug("Set userId {} for audit event with username: {}", user.getId(), username);
                } else {
                    log.debug("User not found for audit event: {}", username);
                    // In production, we might want to create a system user record for audit events
                    // For now, we'll leave userId as null for unknown users
                }
            } catch (Exception e) {
                log.debug("Exception during user lookup for audit event: {}", username, e);
            }
            
            if (!success) {
                event.addRiskFactor("failed_authentication");
                event.setRiskScore(25.0);
            }
            
            saveAuditEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to log authentication event", e);
        }
    }
    
    /**
     * Logs a security incident.
     */
    @Async
    @Transactional
    public void logSecurityIncident(String incidentType, String description, String severity, Object details) {
        logSecurityIncident(incidentType, description, severity, details, new String[]{"security_incident"}, 90.0);
    }
    
    public void logSecurityIncident(String incidentType, String description, String severity, Object details, String[] riskFactors, double riskScore) {
        try {
            AuditEvent event = createBaseEvent(incidentType);
            event.setSeverity(AuditEvent.Severity.valueOf(severity.toUpperCase()));
            event.setDescription(description);
            event.setOutcome("DETECTED");
            event.setResource("security");
            event.setAction("incident_detection");
            
            // Extract userId from details if available
            if (details instanceof Map) {
                Map<String, Object> detailsMap = (Map<String, Object>) details;
                Object userIdObj = detailsMap.get("userId");
                if (userIdObj != null) {
                    try {
                        event.setUserId(UUID.fromString(userIdObj.toString()));
                    } catch (IllegalArgumentException e) {
                        log.debug("Invalid userId format in security incident: {}", userIdObj);
                    }
                }
            }
            
            // Add incident details
            event.setDetails(createIncidentDetailsJson(incidentType, details));
            
            // Set risk factors and score
            if (riskFactors != null) {
                for (String riskFactor : riskFactors) {
                    event.addRiskFactor(riskFactor);
                }
            }
            event.setRiskScore(riskScore);
            
            saveAuditEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to log security incident", e);
        }
    }
    
    /**
     * Logs a user session event.
     */
    @Async
    @Transactional
    public void logSessionEvent(String eventType, String sessionId, String userId, String reason) {
        try {
            AuditEvent event = createBaseEvent(eventType);
            event.setSessionId(sessionId);
            event.setActorType("USER");
            event.setResource("session");
            event.setOutcome("SUCCESS");
            
            // Set userId if provided
            if (userId != null) {
                try {
                    event.setUserId(UUID.fromString(userId));
                } catch (IllegalArgumentException e) {
                    log.debug("Invalid userId format: {}", userId);
                }
            }
            
            event.setDescription(String.format("Session %s%s",
                eventType.toLowerCase().replace("_", " "),
                reason != null ? ": " + reason : ""));
            
            // Add session details
            event.setDetails(createSessionDetailsJson(sessionId, userId, reason));
            
            saveAuditEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to log session event", e);
        }
    }
    
    /**
     * Logs an admin impersonation event.
     */
    @Async
    @Transactional
    public void logImpersonationEvent(String adminUserId, String targetUserId, String justification, boolean start) {
        try {
            String eventType = start ? AuditEvent.EventTypes.ADMIN_IMPERSONATION_START : 
                                      AuditEvent.EventTypes.ADMIN_IMPERSONATION_END;
            
            AuditEvent event = createBaseEvent(eventType);
            event.setActorType("ADMIN");
            event.setTargetType("USER");
            event.setSeverity(AuditEvent.Severity.CRITICAL);
            event.setResource("user_impersonation");
            event.setAction(start ? "start_impersonation" : "end_impersonation");
            event.setOutcome("SUCCESS");
            
            event.setDescription(String.format("Admin %s %s impersonating user %s",
                adminUserId,
                start ? "started" : "stopped",
                targetUserId));
            
            // Add impersonation details
            event.setDetails(createImpersonationDetailsJson(adminUserId, targetUserId, justification));
            
            // High-risk activity
            event.addRiskFactor("admin_impersonation");
            event.setRiskScore(80.0);
            
            saveAuditEvent(event);
            
        } catch (Exception e) {
            log.error("Failed to log impersonation event", e);
        }
    }
    
    /**
     * Creates a base audit event with common fields populated.
     */
    private AuditEvent createBaseEvent(String eventType) {
        AuditEvent event = new AuditEvent();
        event.setTimestamp(Instant.now());
        event.setEventType(eventType);
        event.setSeverity(AuditEvent.Severity.INFO);
        event.setEnvironment(getEnvironment());
        event.setApplicationVersion(getApplicationVersion());
        
        // Set tenant context
        try {
            TenantContextService.TenantContext context = tenantContextService.getCurrentContext();
            if (context != null) {
                event.setTenantId(context.getTenantId());
            }
        } catch (Exception e) {
            log.debug("Could not determine tenant context for audit event");
        }
        
        // Set request context
        populateRequestContext(event);
        
        return event;
    }
    
    /**
     * Populates request context information from the current HTTP request.
     */
    private void populateRequestContext(AuditEvent event) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                event.setIpAddress(getClientIpAddress(request));
                event.setUserAgent(request.getHeader("User-Agent"));
                
                // Add tracing information if available
                event.setTraceId(request.getHeader("X-Trace-ID"));
                event.setSpanId(request.getHeader("X-Span-ID"));
                event.setCorrelationId(request.getHeader("X-Correlation-ID"));
            }
        } catch (Exception e) {
            log.debug("Could not populate request context for audit event", e);
        }
    }
    
    /**
     * Saves an audit event with cryptographic chaining.
     */
    @Transactional
    private void saveAuditEvent(AuditEvent event) {
        try {
            // Get the last event for chaining
            AuditEvent lastEvent = auditEventRepository.findTopByOrderByChainSequenceDesc();
            
            // Set up cryptographic chaining
            long sequence = sequenceCounter.incrementAndGet();
            event.setChainSequence(sequence);
            
            if (lastEvent != null) {
                event.setPreviousEventHash(lastEvent.getEventHash());
            }
            
            // Calculate event hash
            String eventHash = calculateEventHash(event);
            event.setEventHash(eventHash);
            
            // TODO: Add digital signature with HSM/KMS key
            // event.setSignature(signEvent(event));
            // event.setSigningKeyId(getCurrentSigningKeyId());
            
            // Save the event
            auditEventRepository.save(event);
            
            log.debug("Saved audit event: {} with sequence: {}", event.getEventType(), sequence);
            
        } catch (Exception e) {
            log.error("Failed to save audit event", e);
            throw new RuntimeException("Audit logging failed", e);
        }
    }
    
    /**
     * Calculates SHA-256 hash of the audit event for integrity verification.
     */
    private String calculateEventHash(AuditEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Create a canonical representation of the event for hashing
            String canonicalEvent = String.format("%s|%s|%s|%s|%s|%s|%s|%s|%s|%s",
                event.getTimestamp().toString(),
                event.getTenantId(),
                event.getEventType(),
                event.getActorId(),
                event.getTargetId(),
                event.getResource(),
                event.getAction(),
                event.getOutcome(),
                event.getChainSequence(),
                event.getPreviousEventHash() != null ? event.getPreviousEventHash() : "");
            
            byte[] hash = digest.digest(canonicalEvent.getBytes());
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    /**
     * Gets the client IP address from the request, handling proxies.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * Gets the current environment (dev, staging, prod).
     */
    private String getEnvironment() {
        return System.getProperty("spring.profiles.active", "development");
    }
    
    /**
     * Gets the current application version.
     */
    private String getApplicationVersion() {
        return getClass().getPackage().getImplementationVersion();
    }
    
    // JSON creation methods for event details
    
    private String createAuthorizationDetailsJson(AuthorizationRequest request, AuthorizationDecision decision) {
        return String.format("""
            {
                "subject": "%s",
                "resource": "%s",
                "action": "%s",
                "decision": "%s",
                "reason": "%s",
                "evaluationCount": %d,
                "context": %s
            }""",
            request.getSubject(),
            request.getResource(),
            request.getAction(),
            decision.getDecision().toString(),
            decision.getReason(),
            decision.getEvaluations() != null ? decision.getEvaluations().size() : 0,
            request.getContext() != null ? request.getContext().toString() : "{}");
    }
    
    private String createPolicyDetailsJson(TenantPolicy policy, TenantPolicy originalPolicy) {
        return String.format("""
            {
                "policyId": "%s",
                "policyName": "%s",
                "policyType": "%s",
                "effect": "%s",
                "priority": %d,
                "status": "%s",
                "hasChanges": %b
            }""",
            policy.getId(),
            policy.getName(),
            policy.getPolicyType(),
            policy.getEffect(),
            policy.getPriority(),
            policy.getStatus(),
            originalPolicy != null);
    }
    
    private String createAuthenticationDetailsJson(String username, boolean success, String reason) {
        return String.format("""
            {
                "username": "%s",
                "success": %b,
                "reason": "%s",
                "timestamp": "%s"
            }""",
            username,
            success,
            reason != null ? reason : "",
            Instant.now().toString());
    }
    
    private String createIncidentDetailsJson(String incidentType, Object details) {
        try {
            String detailsJson = "{}";
            if (details != null) {
                if (details instanceof Map) {
                    // Convert Map to proper JSON string
                    detailsJson = objectMapper.writeValueAsString(details);
                } else {
                    detailsJson = details.toString();
                }
            }
            
            return String.format("""
                {
                    "incidentType": "%s",
                    "details": %s,
                    "detectedAt": "%s"
                }""",
                incidentType,
                detailsJson,
                Instant.now().toString());
        } catch (Exception e) {
            log.warn("Failed to serialize incident details, using fallback", e);
            return String.format("""
                {
                    "incidentType": "%s",
                    "details": {},
                    "detectedAt": "%s",
                    "error": "Failed to serialize details"
                }""",
                incidentType,
                Instant.now().toString());
        }
    }
    
    private String createSessionDetailsJson(String sessionId, String userId, String reason) {
        return String.format("""
            {
                "sessionId": "%s",
                "userId": "%s",
                "reason": "%s",
                "timestamp": "%s"
            }""",
            sessionId,
            userId != null ? userId : "",
            reason != null ? reason : "",
            Instant.now().toString());
    }
    
    private String createImpersonationDetailsJson(String adminUserId, String targetUserId, String justification) {
        return String.format("""
            {
                "adminUserId": "%s",
                "targetUserId": "%s",
                "justification": "%s",
                "timestamp": "%s"
            }""",
            adminUserId,
            targetUserId,
            justification != null ? justification : "",
            Instant.now().toString());
    }
}
