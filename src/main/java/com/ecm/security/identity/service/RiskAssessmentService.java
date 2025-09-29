package com.ecm.security.identity.service;

import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.domain.UserDevice;
import com.ecm.security.identity.domain.UserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for comprehensive risk assessment of users, sessions, and devices.
 * Implements advanced security scoring algorithms for threat detection.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskAssessmentService {
    
    private final TenantContextService tenantContextService;
    
    /**
     * Calculates comprehensive risk score for a session.
     * Score ranges from 0 (lowest risk) to 100 (highest risk).
     */
    public double calculateSessionRisk(UserSession session, UserDevice device, User user) {
        double riskScore = 0.0;
        
        // Base risk factors
        riskScore += calculateDeviceRisk(device);
        riskScore += calculateUserRisk(user);
        riskScore += calculateSessionContextRisk(session);
        riskScore += calculateLocationRisk(session);
        riskScore += calculateTimeRisk(session);
        riskScore += calculateAuthenticationRisk(session);
        
        // Ensure score is within bounds
        return Math.max(0, Math.min(100, riskScore));
    }
    
    /**
     * Calculates device-specific risk factors.
     */
    public double calculateDeviceRisk(UserDevice device) {
        double deviceRisk = 0.0;
        
        // New or unrecognized device
        if (device.getFirstSeenAt() != null) {
            Duration deviceAge = Duration.between(device.getFirstSeenAt(), Instant.now());
            if (deviceAge.toDays() < 1) {
                deviceRisk += 15.0; // New device is riskier
            } else if (deviceAge.toDays() < 7) {
                deviceRisk += 8.0;
            }
        }
        
        // Device trust score (inverse relationship)
        if (device.getTrustScore() != null) {
            deviceRisk += (100 - device.getTrustScore()) * 0.2;
        }
        
        // Security compromises
        if (device.isBlocked()) {
            deviceRisk += 50.0;
        }
        
        if (device.getJailbrokenRooted()) {
            deviceRisk += 25.0;
        }
        
        if (device.getEmulatorDetected()) {
            deviceRisk += 30.0;
        }
        
        if (device.getTorDetected()) {
            deviceRisk += 20.0;
        }
        
        if (device.getVpnDetected()) {
            deviceRisk += 10.0;
        }
        
        // Authentication failure rate
        if (device.getTotalSessions() > 0) {
            int totalAuth = device.getSuccessfulAuthentications() + device.getFailedAuthentications();
            if (totalAuth > 0) {
                double failureRate = (double) device.getFailedAuthentications() / totalAuth;
                deviceRisk += failureRate * 15.0;
            }
        }
        
        // Device attestation status
        if (!device.getAttestationVerified() && device.getAttestationSupported()) {
            deviceRisk += 5.0;
        }
        
        return deviceRisk;
    }
    
    /**
     * Calculates user-specific risk factors.
     */
    public double calculateUserRisk(User user) {
        double userRisk = 0.0;
        
        // Account status
        if (user.getStatus() == User.UserStatus.SUSPENDED) {
            userRisk += 40.0;
        } else if (user.getStatus() == User.UserStatus.LOCKED) {
            userRisk += 30.0;
        }
        
        // Failed login attempts
        if (user.getFailedLoginAttempts() > 0) {
            userRisk += Math.min(user.getFailedLoginAttempts() * 3.0, 15.0);
        }
        
        // Account age (very new accounts are riskier)
        if (user.getCreatedAt() != null) {
            Duration accountAge = Duration.between(user.getCreatedAt(), Instant.now());
            if (accountAge.toDays() < 1) {
                userRisk += 10.0;
            } else if (accountAge.toDays() < 7) {
                userRisk += 5.0;
            }
        }
        
        // Email verification status
        if (!user.getEmailVerified()) {
            userRisk += 8.0;
        }
        
        // MFA status
        if (!user.getMfaEnabled()) {
            userRisk += 5.0;
        }
        
        // Password age (very old passwords are riskier)
        if (user.getPasswordChangedAt() != null) {
            Duration passwordAge = Duration.between(user.getPasswordChangedAt(), Instant.now());
            if (passwordAge.toDays() > 365) {
                userRisk += 8.0;
            } else if (passwordAge.toDays() > 180) {
                userRisk += 4.0;
            }
        }
        
        // Last activity (dormant accounts awakening can be suspicious)
        if (user.getLastActivityAt() != null) {
            Duration inactivity = Duration.between(user.getLastActivityAt(), Instant.now());
            if (inactivity.toDays() > 90) {
                userRisk += 6.0;
            } else if (inactivity.toDays() > 30) {
                userRisk += 3.0;
            }
        }
        
        return userRisk;
    }
    
    /**
     * Calculates session context risk factors.
     */
    public double calculateSessionContextRisk(UserSession session) {
        double contextRisk = 0.0;
        
        // Impossible travel detection
        if (session.getImpossibleTravelDetected()) {
            contextRisk += 40.0;
        }
        
        // Device fingerprint changes
        if (session.getDeviceFingerprintChanged()) {
            contextRisk += 20.0;
        }
        
        // Session duration (very long sessions can be suspicious)
        if (session.getCreatedAt() != null) {
            Duration sessionDuration = Duration.between(session.getCreatedAt(), Instant.now());
            if (sessionDuration.toHours() > 24) {
                contextRisk += 8.0;
            } else if (sessionDuration.toHours() > 12) {
                contextRisk += 4.0;
            }
        }
        
        // Authentication method risk
        switch (session.getAuthenticationMethod()) {
            case PASSWORD:
                contextRisk += 2.0; // Password-only is riskier
                break;
            case WEBAUTHN:
                contextRisk -= 2.0; // WebAuthn is safer
                break;
            case MAGIC_LINK:
                contextRisk += 1.0;
                break;
            case SOCIAL_OAUTH:
                contextRisk += 1.0;
                break;
            case DEVICE_CODE:
                contextRisk += 3.0; // Device code flow has higher risk
                break;
            default:
                break;
        }
        
        // MFA completion
        if (!session.getMfaCompleted()) {
            contextRisk += 8.0;
        }
        
        return contextRisk;
    }
    
    /**
     * Calculates location-based risk factors.
     */
    public double calculateLocationRisk(UserSession session) {
        double locationRisk = 0.0;
        
        // Check against known high-risk countries
        String country = session.getLocationCountry();
        if (country != null && isHighRiskCountry(country)) {
            locationRisk += 10.0;
        }
        
        // TODO: Implement geolocation-based risk assessment
        // - Distance from user's usual locations
        // - Known VPN exit points
        // - Tor exit nodes
        // - Known compromised IP ranges
        
        return locationRisk;
    }
    
    /**
     * Calculates time-based risk factors.
     */
    public double calculateTimeRisk(UserSession session) {
        double timeRisk = 0.0;
        
        // Check for unusual login times
        Instant now = Instant.now();
        int hour = now.atZone(java.time.ZoneOffset.UTC).getHour();
        
        // Higher risk for very early morning hours (2-6 AM UTC)
        if (hour >= 2 && hour <= 6) {
            timeRisk += 3.0;
        }
        
        // TODO: Implement user-specific time pattern analysis
        // - Compare against user's historical login patterns
        // - Consider user's timezone
        // - Weekend vs weekday patterns
        
        return timeRisk;
    }
    
    /**
     * Calculates authentication-specific risk factors.
     */
    public double calculateAuthenticationRisk(UserSession session) {
        double authRisk = 0.0;
        
        // Check MFA methods used
        if (session.getMfaMethodsUsed() != null) {
            for (String method : session.getMfaMethodsUsed()) {
                switch (method.toUpperCase()) {
                    case "SMS":
                        authRisk += 1.0; // SMS is less secure
                        break;
                    case "EMAIL":
                        authRisk += 0.5;
                        break;
                    case "TOTP":
                        authRisk -= 1.0; // TOTP is more secure
                        break;
                    case "WEBAUTHN":
                        authRisk -= 2.0; // WebAuthn is most secure
                        break;
                }
            }
        }
        
        // Check if step-up auth was required but not completed
        if (session.requiresStepUp()) {
            authRisk += 15.0;
        }
        
        return authRisk;
    }
    
    /**
     * Gets risk factors as a string array for logging and analysis.
     */
    public String[] getRiskFactors(UserSession session, UserDevice device, User user) {
        List<String> factors = new ArrayList<>();
        
        // Device risk factors
        if (device.isBlocked()) {
            factors.add("blocked_device");
        }
        if (device.getJailbrokenRooted()) {
            factors.add("jailbroken_device");
        }
        if (device.getEmulatorDetected()) {
            factors.add("emulator_detected");
        }
        if (device.getTorDetected()) {
            factors.add("tor_usage");
        }
        if (device.getVpnDetected()) {
            factors.add("vpn_usage");
        }
        if (device.getTrustScore() != null && device.getTrustScore() < 30) {
            factors.add("low_device_trust");
        }
        
        // User risk factors
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            factors.add("inactive_user_account");
        }
        if (user.getFailedLoginAttempts() > 3) {
            factors.add("multiple_failed_attempts");
        }
        if (!user.getEmailVerified()) {
            factors.add("unverified_email");
        }
        if (!user.getMfaEnabled()) {
            factors.add("no_mfa_enabled");
        }
        
        // Session risk factors
        if (session.getImpossibleTravelDetected()) {
            factors.add("impossible_travel");
        }
        if (session.getDeviceFingerprintChanged()) {
            factors.add("device_fingerprint_change");
        }
        if (!session.getMfaCompleted()) {
            factors.add("mfa_not_completed");
        }
        if (session.requiresStepUp()) {
            factors.add("step_up_required");
        }
        
        // Location risk factors
        if (session.getLocationCountry() != null && isHighRiskCountry(session.getLocationCountry())) {
            factors.add("high_risk_location");
        }
        
        return factors.toArray(new String[0]);
    }
    
    /**
     * Determines if a country is considered high-risk.
     * In production, this should be configurable and based on threat intelligence.
     */
    private boolean isHighRiskCountry(String countryCode) {
        // This is a simplified example - in production, use threat intelligence feeds
        String[] highRiskCountries = {
            // Add countries based on your organization's risk assessment
            // This should be configurable and regularly updated
        };
        
        for (String riskCountry : highRiskCountries) {
            if (riskCountry.equalsIgnoreCase(countryCode)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Calculates risk score for a specific action.
     */
    public double calculateActionRisk(String action, UserSession session, Object context) {
        double actionRisk = session.getRiskScore();
        
        // Add action-specific risk factors
        switch (action.toLowerCase()) {
            case "delete":
            case "admin":
                actionRisk += 10.0;
                break;
            case "export":
            case "download":
                actionRisk += 5.0;
                break;
            case "create":
            case "update":
                actionRisk += 2.0;
                break;
            case "read":
            case "list":
                // No additional risk
                break;
            default:
                actionRisk += 1.0; // Unknown actions get slight risk increase
                break;
        }
        
        return Math.max(0, Math.min(100, actionRisk));
    }
    
    /**
     * Determines if additional verification is needed based on risk assessment.
     */
    public boolean requiresAdditionalVerification(double riskScore, String action) {
        TenantContextService.TenantSettings settings = tenantContextService.getTenantSettings(
            tenantContextService.getCurrentTenantId());
        
        double threshold = settings.getRiskPolicy().getMaxRiskScore();
        
        // Adjust threshold based on action sensitivity
        switch (action.toLowerCase()) {
            case "delete":
            case "admin":
                threshold = Math.max(threshold - 20, 30); // Lower threshold for sensitive actions
                break;
            case "export":
            case "download":
                threshold = Math.max(threshold - 10, 40);
                break;
        }
        
        return riskScore > threshold;
    }
}
