package com.ecm.security.identity.service;

import com.ecm.security.identity.domain.Consent;
import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.repository.ConsentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing user consent operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsentService {

    private final ConsentRepository consentRepository;
    private final ObjectMapper objectMapper;

    /**
     * Grant consent to a user for an application.
     */
    @Transactional
    public Consent grantConsent(User user, String applicationId, String consentType, 
                               Map<String, Object> permissions, Map<String, Object> metadata) {
        
        log.info("Granting consent for user {} to application {}", user.getEmail(), applicationId);
        
        // Check if there's already an active consent
        Optional<Consent> existingConsent = consentRepository.findActiveConsentByUserAndApplication(user, applicationId);
        if (existingConsent.isPresent()) {
            // Revoke existing consent
            existingConsent.get().revoke("Replaced by new consent");
            consentRepository.save(existingConsent.get());
        }
        
        // Create new consent
        Consent consent = Consent.builder()
                .user(user)
                .tenant(user.getTenant())
                .applicationId(applicationId)
                .consentType(Consent.ConsentType.valueOf(consentType.toUpperCase()))
                .status(Consent.ConsentStatus.ACTIVE)
                .grantedAt(Instant.now())
                .permissions(serializePermissions(permissions))
                .consentMetadata(serializeMetadata(metadata))
                .grantedPermissionsCount(countGrantedPermissions(permissions))
                .deniedPermissionsCount(countDeniedPermissions(permissions))
                .consentVersion(extractString(metadata, "consentVersion"))
                .privacyPolicyVersion(extractString(metadata, "privacyPolicyVersion"))
                .userAgent(extractString(metadata, "userAgent"))
                .build();
        
        return consentRepository.save(consent);
    }

    /**
     * Get all consents for a user.
     */
    public List<Consent> getUserConsents(User user) {
        return consentRepository.findByUserOrderByGrantedAtDesc(user);
    }

    /**
     * Get active consents for a user.
     */
    public List<Consent> getActiveUserConsents(User user) {
        return consentRepository.findActiveConsentsByUser(user);
    }

    /**
     * Validate consent for a specific resource and action.
     */
    public boolean validateConsent(User user, String applicationId, String resource, String action) {
        Optional<Consent> consent = consentRepository.findActiveConsentByUserAndApplication(user, applicationId);
        return consent.isPresent() && consent.get().isActive() && consent.get().hasPermission(resource, action);
    }

    /**
     * Modify existing consent.
     */
    @Transactional
    public Consent modifyConsent(UUID consentId, Map<String, Object> newPermissions, Map<String, Object> metadata) {
        Optional<Consent> consentOpt = consentRepository.findById(consentId);
        if (consentOpt.isEmpty()) {
            throw new IllegalArgumentException("Consent not found: " + consentId);
        }
        
        Consent consent = consentOpt.get();
        consent.setPermissions(serializePermissions(newPermissions));
        consent.setConsentMetadata(serializeMetadata(metadata));
        consent.setGrantedPermissionsCount(countGrantedPermissions(newPermissions));
        consent.setDeniedPermissionsCount(countDeniedPermissions(newPermissions));
        
        return consentRepository.save(consent);
    }

    /**
     * Revoke consent.
     */
    @Transactional
    public Consent revokeConsent(UUID consentId, String reason) {
        Optional<Consent> consentOpt = consentRepository.findById(consentId);
        if (consentOpt.isEmpty()) {
            throw new IllegalArgumentException("Consent not found: " + consentId);
        }
        
        Consent consent = consentOpt.get();
        consent.revoke(reason);
        
        return consentRepository.save(consent);
    }

    /**
     * Get consent audit trail.
     */
    public List<Map<String, Object>> getConsentAuditTrail(UUID consentId) {
        // For now, create mock audit trail entries. In a real implementation, 
        // this would return a history of changes from an audit table
        Optional<Consent> consentOpt = consentRepository.findById(consentId);
        if (consentOpt.isEmpty()) {
            return List.of();
        }
        
        Consent consent = consentOpt.get();
        List<Map<String, Object>> auditTrail = new ArrayList<>();
        
        // Add grant event
        Map<String, Object> grantEvent = new HashMap<>();
        grantEvent.put("timestamp", consent.getGrantedAt().toString());
        grantEvent.put("action", "GRANTED");
        grantEvent.put("details", "Consent granted for application: " + consent.getApplicationId());
        auditTrail.add(grantEvent);
        
        // Add modify event if consent was modified
        if (consent.getGrantedPermissionsCount() > 0 || consent.getDeniedPermissionsCount() > 0) {
            Map<String, Object> modifyEvent = new HashMap<>();
            modifyEvent.put("timestamp", consent.getGrantedAt().plusSeconds(60).toString()); // Mock timestamp
            modifyEvent.put("action", "MODIFIED");
            modifyEvent.put("details", "Consent modified - granted: " + consent.getGrantedPermissionsCount() + 
                    ", denied: " + consent.getDeniedPermissionsCount());
            auditTrail.add(modifyEvent);
        }
        
        // Add revoke event if consent was revoked
        if (consent.getStatus() == Consent.ConsentStatus.REVOKED) {
            Map<String, Object> revokeEvent = new HashMap<>();
            revokeEvent.put("timestamp", consent.getRevokedAt().toString());
            revokeEvent.put("action", "REVOKED");
            revokeEvent.put("details", "Consent revoked - reason: " + consent.getRevocationReason());
            auditTrail.add(revokeEvent);
        }
        
        return auditTrail;
    }

    private String serializePermissions(Map<String, Object> permissions) {
        try {
            return objectMapper.writeValueAsString(permissions);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize permissions", e);
            return "{}";
        }
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize metadata", e);
            return "{}";
        }
    }

    private int countGrantedPermissions(Map<String, Object> permissions) {
        if (permissions == null) return 0;
        
        int count = 0;
        for (Object permission : permissions.values()) {
            if (permission instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> permMap = (Map<String, Object>) permission;
                Boolean granted = (Boolean) permMap.get("granted");
                if (Boolean.TRUE.equals(granted)) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countDeniedPermissions(Map<String, Object> permissions) {
        if (permissions == null) return 0;
        
        int count = 0;
        for (Object permission : permissions.values()) {
            if (permission instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> permMap = (Map<String, Object>) permission;
                Boolean granted = (Boolean) permMap.get("granted");
                if (Boolean.FALSE.equals(granted)) {
                    count++;
                }
            }
        }
        return count;
    }

    private String extractString(Map<String, Object> metadata, String key) {
        if (metadata == null) return null;
        Object value = metadata.get(key);
        return value != null ? value.toString() : null;
    }
}
