package com.ecm.security.identity.service;

import com.ecm.security.identity.domain.*;
import com.ecm.security.identity.repository.UserSessionRepository;
import com.ecm.security.identity.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user sessions and devices with advanced security features.
 * Handles session lifecycle, risk assessment, and proactive security measures.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SessionManagementService {
    
    private final UserSessionRepository sessionRepository;
    private final UserDeviceRepository deviceRepository;
    private final AuditService auditService;
    private final RiskAssessmentService riskAssessmentService;
    private final TenantContextService tenantContextService;
    
    /**
     * Creates a new user session with comprehensive security tracking.
     */
    public UserSession createSession(User user, SessionCreationRequest request) {
        log.info("Creating session for user: {} from IP: {}", user.getEmail(), request.getIpAddress());
        
        // Check for concurrent session limits
        enforceSessionLimits(user);
        
        // Find or create device record
        UserDevice device = findOrCreateDevice(user, request);
        
        // Create the session
        UserSession session = new UserSession();
        session.setUser(user);
        session.setDevice(device);
        session.setSessionId(generateSessionId());
        session.setRefreshTokenHash(generateRefreshTokenHash());
        session.setRefreshTokenFamily(generateTokenFamily());
        session.setStatus(UserSession.SessionStatus.ACTIVE);
        session.setExpiresAt(calculateSessionExpiration(request));
        session.setLastActivityAt(Instant.now());
        session.setIpAddress(request.getIpAddress());
        session.setUserAgent(request.getUserAgent());
        session.setLocationCountry(request.getLocationCountry());
        session.setLocationCity(request.getLocationCity());
        session.setLocationLatitude(request.getLocationLatitude());
        session.setLocationLongitude(request.getLocationLongitude());
        session.setAuthenticationMethod(request.getAuthenticationMethod());
        session.setMfaCompleted(request.isMfaCompleted());
        session.setMfaMethodsUsed(request.getMfaMethodsUsed());
        session.setClientAppId(request.getClientAppId());
        session.setScopes(request.getScopes());
        session.setConsentGiven(request.isConsentGiven());
        session.setConsentScopes(request.getConsentScopes());
        
        // Perform risk assessment
        performRiskAssessment(session, device, user);
        
        // Save session and update device
        session = sessionRepository.save(session);
        device.recordSession();
        deviceRepository.save(device);
        
        // Update user's last activity
        user.recordSuccessfulLogin(request.getIpAddress());
        
        // Audit the session creation
        auditService.logSessionEvent(AuditEvent.EventTypes.SESSION_CREATED, 
                                    session.getSessionId(), 
                                    user.getId().toString(), 
                                    "Session created from " + request.getIpAddress());
        
        log.info("Created session {} for user {}", session.getSessionId(), user.getEmail());
        return session;
    }
    
    /**
     * Validates and extends an existing session.
     */
    public Optional<UserSession> validateAndExtendSession(String sessionId, String ipAddress) {
        Optional<UserSession> sessionOpt = sessionRepository.findBySessionIdAndStatus(
            sessionId, UserSession.SessionStatus.ACTIVE);
        
        if (sessionOpt.isEmpty()) {
            log.debug("Session not found or inactive: {}", sessionId);
            return Optional.empty();
        }
        
        UserSession session = sessionOpt.get();
        
        // Check if session is expired
        if (session.isExpired()) {
            log.info("Session expired: {}", sessionId);
            terminateSession(sessionId, "Session expired");
            return Optional.empty();
        }
        
        // Check for suspicious activity
        if (detectSuspiciousActivity(session, ipAddress)) {
            log.warn("Suspicious activity detected for session: {}", sessionId);
            invalidateSessionDueToRisk(session, "Suspicious activity detected");
            return Optional.empty();
        }
        
        // Update session activity
        session.updateActivity();
        session.setIpAddress(ipAddress);
        
        // Re-assess risk
        updateSessionRisk(session);
        
        // Extend session if low risk
        if (session.getRiskLevel() == UserSession.RiskLevel.LOW || 
            session.getRiskLevel() == UserSession.RiskLevel.MEDIUM) {
            extendSessionExpiration(session);
        }
        
        sessionRepository.save(session);
        
        return Optional.of(session);
    }
    
    /**
     * Terminates a session gracefully.
     */
    public void terminateSession(String sessionId, String reason) {
        Optional<UserSession> sessionOpt = sessionRepository.findBySessionId(sessionId);
        
        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            session.terminate(reason);
            sessionRepository.save(session);
            
            // Audit session termination
            auditService.logSessionEvent(AuditEvent.EventTypes.SESSION_TERMINATED, 
                                        sessionId, 
                                        session.getUser().getId().toString(), 
                                        reason);
            
            log.info("Terminated session {} for user {}: {}", 
                     sessionId, session.getUser().getEmail(), reason);
        }
    }
    
    /**
     * Terminates all sessions for a user.
     */
    public void terminateAllUserSessions(UUID userId, String reason) {
        List<UserSession> activeSessions = sessionRepository.findActiveSessionsByUserId(userId);
        
        for (UserSession session : activeSessions) {
            session.terminate(reason);
            
            // Audit each session termination
            auditService.logSessionEvent(AuditEvent.EventTypes.SESSION_TERMINATED, 
                                        session.getSessionId(), 
                                        userId.toString(), 
                                        reason);
        }
        
        sessionRepository.saveAll(activeSessions);
        
        log.info("Terminated {} sessions for user {}: {}", 
                 activeSessions.size(), userId, reason);
    }
    
    /**
     * Invalidates sessions based on risk assessment.
     */
    public void invalidateHighRiskSessions() {
        List<UserSession> highRiskSessions = sessionRepository.findByRiskLevelAndStatus(
            UserSession.RiskLevel.HIGH, UserSession.SessionStatus.ACTIVE);
        
        for (UserSession session : highRiskSessions) {
            invalidateSessionDueToRisk(session, "High risk score detected");
        }
        
        List<UserSession> criticalRiskSessions = sessionRepository.findByRiskLevelAndStatus(
            UserSession.RiskLevel.CRITICAL, UserSession.SessionStatus.ACTIVE);
        
        for (UserSession session : criticalRiskSessions) {
            invalidateSessionDueToRisk(session, "Critical risk score detected");
        }
        
        log.info("Invalidated {} high-risk and {} critical-risk sessions", 
                 highRiskSessions.size(), criticalRiskSessions.size());
    }
    
    /**
     * Gets active sessions for a user.
     */
    public List<UserSession> getUserActiveSessions(UUID userId) {
        return sessionRepository.findActiveSessionsByUserId(userId);
    }
    
    /**
     * Gets session by ID if active.
     */
    public Optional<UserSession> getActiveSession(String sessionId) {
        return sessionRepository.findBySessionIdAndStatus(sessionId, UserSession.SessionStatus.ACTIVE);
    }
    
    /**
     * Detects impossible travel between sessions.
     */
    public boolean detectImpossibleTravel(UserSession session, String newIpAddress, 
                                         Double newLatitude, Double newLongitude) {
        if (session.getLocationLatitude() == null || session.getLocationLongitude() == null ||
            newLatitude == null || newLongitude == null) {
            return false; // Can't detect without location data
        }
        
        // Calculate distance and time
        double distance = calculateDistance(
            session.getLocationLatitude(), session.getLocationLongitude(),
            newLatitude, newLongitude
        );
        
        Duration timeDiff = Duration.between(session.getLastActivityAt(), Instant.now());
        double hours = timeDiff.toMinutes() / 60.0;
        
        if (hours > 0) {
            double speed = distance / hours; // km/h
            
            // Impossible travel threshold (500 km/h considering commercial flights)
            if (speed > 500) {
                log.warn("Impossible travel detected for session {}: {} km in {} hours ({} km/h)",
                         session.getSessionId(), distance, hours, speed);
                
                // Mark session as suspicious
                session.setImpossibleTravelDetected(true);
                session.updateRiskLevel(UserSession.RiskLevel.CRITICAL, 100.0, 
                                      new String[]{"impossible_travel"});
                
                // Audit the incident
                auditService.logSecurityIncident(
                    AuditEvent.EventTypes.IMPOSSIBLE_TRAVEL,
                    String.format("Impossible travel detected: %.2f km in %.2f hours (%.2f km/h)", 
                                  distance, hours, speed),
                    "CRITICAL",
                    Map.of(
                        "sessionId", session.getSessionId(),
                        "distance", distance,
                        "timeHours", hours,
                        "speed", speed,
                        "previousLocation", session.getLocationCity(),
                        "newIpAddress", newIpAddress
                    )
                );
                
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Enforces concurrent session limits for a user.
     */
    private void enforceSessionLimits(User user) {
        TenantContextService.TenantSettings settings = tenantContextService.getTenantSettings(
            user.getTenant().getId());
        
        int maxSessions = settings.getSessionPolicy().getMaxSessions();
        List<UserSession> activeSessions = sessionRepository.findActiveSessionsByUserId(user.getId());
        
        if (activeSessions.size() >= maxSessions) {
            // Terminate oldest session
            UserSession oldestSession = activeSessions.stream()
                .min((s1, s2) -> s1.getLastActivityAt().compareTo(s2.getLastActivityAt()))
                .orElse(null);
            
            if (oldestSession != null) {
                terminateSession(oldestSession.getSessionId(), 
                               "Session limit exceeded - oldest session terminated");
            }
        }
    }
    
    /**
     * Finds or creates a device record for the user.
     */
    private UserDevice findOrCreateDevice(User user, SessionCreationRequest request) {
        String deviceFingerprint = request.getDeviceFingerprint();
        
        Optional<UserDevice> deviceOpt = deviceRepository.findByUserAndDeviceFingerprint(
            user, deviceFingerprint);
        
        if (deviceOpt.isPresent()) {
            UserDevice device = deviceOpt.get();
            device.recordAuthentication(true);
            return device;
        }
        
        // Create new device
        UserDevice device = new UserDevice();
        device.setUser(user);
        device.setDeviceFingerprint(deviceFingerprint);
        device.setDeviceName(request.getDeviceName());
        device.setDeviceType(request.getDeviceType());
        device.setOperatingSystem(request.getOperatingSystem());
        device.setBrowser(request.getBrowser());
        device.setBrowserVersion(request.getBrowserVersion());
        device.setPlatform(request.getPlatform());
        device.setScreenResolution(request.getScreenResolution());
        device.setTimezone(request.getTimezone());
        device.setLanguage(request.getLanguage());
        device.setUserAgent(request.getUserAgent());
        device.setIpAddress(request.getIpAddress());
        device.setGeolocationCountry(request.getLocationCountry());
        device.setGeolocationCity(request.getLocationCity());
        device.setFirstSeenAt(Instant.now());
        device.setLastSeenAt(Instant.now());
        device.recordAuthentication(true);
        
        return deviceRepository.save(device);
    }
    
    /**
     * Performs comprehensive risk assessment for a new session.
     */
    private void performRiskAssessment(UserSession session, UserDevice device, User user) {
        double riskScore = riskAssessmentService.calculateSessionRisk(session, device, user);
        
        UserSession.RiskLevel riskLevel;
        if (riskScore >= 90) {
            riskLevel = UserSession.RiskLevel.CRITICAL;
        } else if (riskScore >= 70) {
            riskLevel = UserSession.RiskLevel.HIGH;
        } else if (riskScore >= 40) {
            riskLevel = UserSession.RiskLevel.MEDIUM;
        } else {
            riskLevel = UserSession.RiskLevel.LOW;
        }
        
        session.setRiskLevel(riskLevel);
        session.setRiskScore(riskScore);
        session.setRiskFactors(riskAssessmentService.getRiskFactors(session, device, user));
    }
    
    /**
     * Updates session risk based on current activity.
     */
    private void updateSessionRisk(UserSession session) {
        double newRiskScore = riskAssessmentService.calculateSessionRisk(
            session, session.getDevice(), session.getUser());
        
        if (newRiskScore > session.getRiskScore() + 10) {
            // Significant risk increase
            UserSession.RiskLevel newRiskLevel;
            if (newRiskScore >= 90) {
                newRiskLevel = UserSession.RiskLevel.CRITICAL;
            } else if (newRiskScore >= 70) {
                newRiskLevel = UserSession.RiskLevel.HIGH;
            } else if (newRiskScore >= 40) {
                newRiskLevel = UserSession.RiskLevel.MEDIUM;
            } else {
                newRiskLevel = UserSession.RiskLevel.LOW;
            }
            
            session.updateRiskLevel(newRiskLevel, newRiskScore, 
                                  riskAssessmentService.getRiskFactors(session, session.getDevice(), session.getUser()));
            
            log.info("Updated risk for session {}: {} -> {} (score: {})", 
                     session.getSessionId(), session.getRiskLevel(), newRiskLevel, newRiskScore);
        }
    }
    
    /**
     * Detects suspicious activity in session.
     */
    private boolean detectSuspiciousActivity(UserSession session, String currentIpAddress) {
        // Check for IP address changes
        if (!session.getIpAddress().equals(currentIpAddress)) {
            log.debug("IP address change detected for session {}: {} -> {}", 
                     session.getSessionId(), session.getIpAddress(), currentIpAddress);
            
            // This could be suspicious if it's a significant geographical change
            // For now, we'll just log it and continue monitoring
        }
        
        // Check for rapid successive requests (potential bot activity)
        if (session.getLastActivityAt() != null) {
            Duration timeSinceLastActivity = Duration.between(session.getLastActivityAt(), Instant.now());
            if (timeSinceLastActivity.toMillis() < 100) {
                // Very rapid requests - potential bot
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Invalidates a session due to risk concerns.
     */
    private void invalidateSessionDueToRisk(UserSession session, String reason) {
        session.setStatus(UserSession.SessionStatus.INVALIDATED);
        session.setTerminatedAt(Instant.now());
        session.setTerminationReason(reason);
        
        sessionRepository.save(session);
        
        // Audit the security action
        auditService.logSecurityIncident(
            AuditEvent.EventTypes.SESSION_HIJACK_DETECTED,
            "Session invalidated due to security concerns: " + reason,
            "HIGH",
            Map.of(
                "sessionId", session.getSessionId(),
                "userId", session.getUser().getId().toString(),
                "riskScore", session.getRiskScore(),
                "riskLevel", session.getRiskLevel().toString()
            )
        );
        
        log.warn("Invalidated session {} due to risk: {}", session.getSessionId(), reason);
    }
    
    /**
     * Extends session expiration for active sessions.
     */
    private void extendSessionExpiration(UserSession session) {
        TenantContextService.TenantSettings settings = tenantContextService.getTenantSettings(
            session.getUser().getTenant().getId());
        
        int sessionTimeout = settings.getSessionPolicy().getSessionTimeout();
        session.setExpiresAt(Instant.now().plusSeconds(sessionTimeout));
    }
    
    /**
     * Calculates session expiration time based on request and tenant settings.
     */
    private Instant calculateSessionExpiration(SessionCreationRequest request) {
        TenantContextService.TenantSettings settings = tenantContextService.getTenantSettings(
            tenantContextService.getCurrentTenantId());
        
        int sessionTimeout = request.isRememberMe() ? 
            settings.getSessionPolicy().getExtendedSessionTimeout() :
            settings.getSessionPolicy().getSessionTimeout();
        
        return Instant.now().plusSeconds(sessionTimeout);
    }
    
    /**
     * Calculates distance between two geographic points using Haversine formula.
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radius of the earth in km
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c; // Distance in km
    }
    
    /**
     * Generates a unique session ID.
     */
    private String generateSessionId() {
        return "sess_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Generates a refresh token hash.
     */
    private String generateRefreshTokenHash() {
        return "rft_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Generates a token family identifier for refresh token rotation.
     */
    private String generateTokenFamily() {
        return "fam_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Scheduled task to clean up expired sessions.
     */
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    @Async
    public void cleanupExpiredSessions() {
        List<UserSession> expiredSessions = sessionRepository.findExpiredSessions(Instant.now());
        
        for (UserSession session : expiredSessions) {
            session.setStatus(UserSession.SessionStatus.EXPIRED);
            session.setTerminatedAt(Instant.now());
            session.setTerminationReason("Session expired");
        }
        
        if (!expiredSessions.isEmpty()) {
            sessionRepository.saveAll(expiredSessions);
            log.info("Cleaned up {} expired sessions", expiredSessions.size());
        }
    }
    
    /**
     * Scheduled task to perform risk assessment on active sessions.
     */
    @Scheduled(fixedDelay = 600000) // Every 10 minutes
    @Async
    public void performSessionRiskAssessment() {
        List<UserSession> activeSessions = sessionRepository.findByStatus(UserSession.SessionStatus.ACTIVE);
        
        int invalidatedCount = 0;
        for (UserSession session : activeSessions) {
            try {
                updateSessionRisk(session);
                
                if (session.isHighRisk()) {
                    invalidateSessionDueToRisk(session, "Elevated risk detected during periodic assessment");
                    invalidatedCount++;
                }
            } catch (Exception e) {
                log.error("Error during risk assessment for session: {}", session.getSessionId(), e);
            }
        }
        
        if (invalidatedCount > 0) {
            log.info("Risk assessment completed: invalidated {} high-risk sessions", invalidatedCount);
        }
    }
    
    // Supporting classes
    
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SessionCreationRequest {
        private String ipAddress;
        private String userAgent;
        private String locationCountry;
        private String locationCity;
        private Double locationLatitude;
        private Double locationLongitude;
        private UserSession.AuthenticationMethod authenticationMethod;
        private boolean mfaCompleted;
        private String[] mfaMethodsUsed;
        private String clientAppId;
        private String[] scopes;
        private boolean consentGiven;
        private String[] consentScopes;
        private boolean rememberMe;
        
        // Device information
        private String deviceFingerprint;
        private String deviceName;
        private String deviceType;
        private String operatingSystem;
        private String browser;
        private String browserVersion;
        private String platform;
        private String screenResolution;
        private String timezone;
        private String language;
    }
}
