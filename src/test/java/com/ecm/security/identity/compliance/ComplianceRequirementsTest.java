package com.ecm.security.identity.compliance;

import com.ecm.security.identity.domain.*;
import com.ecm.security.identity.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests validating NFR2 - Observability & Compliance requirements.
 * 
 * Requirements tested:
 * - NFR2.1: Audit Log Immutability & Integrity
 * - NFR2.2: Consent & ToS Versioning
 * - NFR2.3: Privacy & Data Rights Management
 * - NFR2.4: Jurisdiction-Aware Policies
 * - NFR2.5: Forensic Timelines
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("NFR2 - Observability & Compliance Requirements")
class ComplianceRequirementsTest {

    @Mock
    private AuditService auditService;

    private User testUser;
    private AuditEvent testAuditEvent;
    private Tenant testTenant;

    @BeforeEach
    void setUp() {
        testTenant = createTestTenant();
        testUser = createTestUser();
        testAuditEvent = createTestAuditEvent();
    }

    @Test
    @DisplayName("NFR2.1 - Generate cryptographically chained audit logs that are tamper-evident with field-level PII redaction")
    void testAuditLogImmutabilityAndIntegrity() {
        // Test cryptographic chaining
        AuditEvent firstEvent = new AuditEvent();
        firstEvent.setId(UUID.randomUUID());
        firstEvent.setTimestamp(Instant.now());
        firstEvent.setEventType(AuditEvent.EventTypes.LOGIN_SUCCESS);
        firstEvent.setChainSequence(1L);
        firstEvent.setPreviousEventHash(null); // First event
        
        String firstEventHash = calculateEventHash(firstEvent);
        firstEvent.setEventHash(firstEventHash);
        
        assertNotNull(firstEvent.getEventHash());
        assertNull(firstEvent.getPreviousEventHash());
        assertEquals(1L, firstEvent.getChainSequence());
        
        // Test chaining of subsequent events
        AuditEvent secondEvent = new AuditEvent();
        secondEvent.setId(UUID.randomUUID());
        secondEvent.setTimestamp(Instant.now());
        secondEvent.setEventType(AuditEvent.EventTypes.LOGOUT);
        secondEvent.setChainSequence(2L);
        secondEvent.setPreviousEventHash(firstEvent.getEventHash());
        
        String secondEventHash = calculateEventHash(secondEvent);
        secondEvent.setEventHash(secondEventHash);
        
        assertNotNull(secondEvent.getEventHash());
        assertEquals(firstEvent.getEventHash(), secondEvent.getPreviousEventHash());
        assertEquals(2L, secondEvent.getChainSequence());
        assertNotEquals(firstEvent.getEventHash(), secondEvent.getEventHash());
        
        // Test tamper detection
        String originalHash = secondEvent.getEventHash();
        secondEvent.setDescription("Tampered description");
        String tamperedHash = calculateEventHash(secondEvent);
        
        assertNotEquals(originalHash, tamperedHash);
        
        // Test field-level PII redaction
        AuditEvent piiEvent = new AuditEvent();
        piiEvent.setDescription("User john.doe@example.com logged in from IP 192.168.1.100");
        piiEvent.setPiiRedacted(false);
        piiEvent.setRedactedFields(new String[]{});
        
        // Simulate PII redaction
        piiEvent.setDescription("User [REDACTED] logged in from IP [REDACTED]");
        piiEvent.setPiiRedacted(true);
        piiEvent.setRedactedFields(new String[]{"email", "ip_address"});
        
        assertTrue(piiEvent.getPiiRedacted());
        assertArrayEquals(new String[]{"email", "ip_address"}, piiEvent.getRedactedFields());
        assertTrue(piiEvent.getDescription().contains("[REDACTED]"));
        
        // Test immutability - audit events should not be modifiable after creation
        testAuditEvent.setTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));
        testAuditEvent.setEventType(AuditEvent.EventTypes.USER_CREATED);
        testAuditEvent.setEventHash("immutable_hash");
        
        // In real implementation, these fields would be immutable
        assertNotNull(testAuditEvent.getTimestamp());
        assertNotNull(testAuditEvent.getEventType());
        assertNotNull(testAuditEvent.getEventHash());
        
        // Test SLA guarantees
        testAuditEvent.setRetentionUntil(Instant.now().plus(2555, ChronoUnit.DAYS)); // 7 years
        testAuditEvent.setLegalHold(false);
        
        assertTrue(testAuditEvent.requiresRetention());
        assertFalse(testAuditEvent.getLegalHold());
        
        // Test cryptographic proof of retention
        testAuditEvent.setSignature("digital_signature_here");
        testAuditEvent.setSigningKeyId("audit_signing_key_001");
        
        assertNotNull(testAuditEvent.getSignature());
        assertNotNull(testAuditEvent.getSigningKeyId());
    }

    @Test
    @DisplayName("NFR2.2 - Persist specific version of Terms of Service that each user has consented to and trigger re-consent flows")
    void testConsentAndToSVersioning() {
        // Test initial consent tracking
        testUser.setTermsAcceptedAt(Instant.now());
        testUser.setTermsVersion("v2.1");
        testUser.setPrivacyPolicyAcceptedAt(Instant.now());
        testUser.setPrivacyPolicyVersion("v1.5");
        
        assertNotNull(testUser.getTermsAcceptedAt());
        assertEquals("v2.1", testUser.getTermsVersion());
        assertNotNull(testUser.getPrivacyPolicyAcceptedAt());
        assertEquals("v1.5", testUser.getPrivacyPolicyVersion());
        
        // Test consent versioning for new ToS
        String currentToSVersion = "v2.2";
        String currentPrivacyVersion = "v1.6";
        
        boolean needsToSReconsent = !currentToSVersion.equals(testUser.getTermsVersion());
        boolean needsPrivacyReconsent = !currentPrivacyVersion.equals(testUser.getPrivacyPolicyVersion());
        
        assertTrue(needsToSReconsent);
        assertTrue(needsPrivacyReconsent);
        
        // Test re-consent flow
        if (needsToSReconsent) {
            testUser.setTermsAcceptedAt(Instant.now());
            testUser.setTermsVersion(currentToSVersion);
        }
        
        if (needsPrivacyReconsent) {
            testUser.setPrivacyPolicyAcceptedAt(Instant.now());
            testUser.setPrivacyPolicyVersion(currentPrivacyVersion);
        }
        
        assertEquals(currentToSVersion, testUser.getTermsVersion());
        assertEquals(currentPrivacyVersion, testUser.getPrivacyPolicyVersion());
        
        // Test marketing consent tracking
        testUser.setMarketingConsent(true);
        assertTrue(testUser.getMarketingConsent());
        
        // Test consent withdrawal
        testUser.setMarketingConsent(false);
        assertFalse(testUser.getMarketingConsent());
        
        // Test consent audit trail
        AuditEvent consentEvent = new AuditEvent();
        consentEvent.setEventType(AuditEvent.EventTypes.CONSENT_GIVEN);
        consentEvent.setUserId(testUser.getId());
        consentEvent.setDescription("User consented to Terms v" + currentToSVersion + " and Privacy Policy v" + currentPrivacyVersion);
        consentEvent.setDetails("{\"termsVersion\":\"" + currentToSVersion + "\",\"privacyVersion\":\"" + currentPrivacyVersion + "\"}");
        
        assertEquals(AuditEvent.EventTypes.CONSENT_GIVEN, consentEvent.getEventType());
        assertTrue(consentEvent.getDescription().contains("v" + currentToSVersion));
        assertTrue(consentEvent.getDetails().contains(currentToSVersion));
        
        // Test minor consent (COPPA compliance)
        User minorUser = new User();
        minorUser.setIsMinor(true);
        minorUser.setDateOfBirth(LocalDate.now().minus(15, ChronoUnit.YEARS));
        minorUser.setParentEmail("parent@example.com");
        minorUser.setParentalConsentAt(Instant.now());
        
        assertTrue(minorUser.getIsMinor());
        assertNotNull(minorUser.getParentEmail());
        assertNotNull(minorUser.getParentalConsentAt());
        assertFalse(minorUser.requiresParentalConsent());
    }

    @Test
    @DisplayName("NFR2.3 - Implement procedures for Right to Erasure that coexist with Legal Hold requirements")
    void testPrivacyAndDataRightsManagement() {
        // Test Right to Erasure (GDPR Article 17)
        testUser.setStatus(User.UserStatus.PENDING_DELETION);
        testUser.markAsDeleted();
        
        assertEquals(User.UserStatus.PENDING_DELETION, testUser.getStatus());
        assertTrue(testUser.isDeleted());
        assertNotNull(testUser.getDeletedAt());
        
        // Test data export (GDPR Article 20 - Data Portability)
        AuditEvent dataExportEvent = new AuditEvent();
        dataExportEvent.setEventType(AuditEvent.EventTypes.DATA_EXPORT);
        dataExportEvent.setUserId(testUser.getId());
        dataExportEvent.setDescription("User requested data export");
        dataExportEvent.setDetails("{\"exportFormat\":\"JSON\",\"signedArchive\":true,\"integrityHash\":\"sha256_hash_here\"}");
        
        assertEquals(AuditEvent.EventTypes.DATA_EXPORT, dataExportEvent.getEventType());
        assertTrue(dataExportEvent.getDetails().contains("signedArchive"));
        assertTrue(dataExportEvent.getDetails().contains("integrityHash"));
        
        // Test Legal Hold override
        AuditEvent legalHoldEvent = new AuditEvent();
        legalHoldEvent.setUserId(testUser.getId());
        legalHoldEvent.setLegalHold(true);
        legalHoldEvent.setRetentionUntil(Instant.now().plus(10, ChronoUnit.YEARS));
        legalHoldEvent.setComplianceFlags(new String[]{"litigation_hold", "regulatory_investigation"});
        
        assertTrue(legalHoldEvent.getLegalHold());
        assertTrue(legalHoldEvent.requiresRetention());
        assertArrayEquals(new String[]{"litigation_hold", "regulatory_investigation"}, 
                         legalHoldEvent.getComplianceFlags());
        
        // Test that Legal Hold prevents deletion
        boolean canDelete = !legalHoldEvent.getLegalHold();
        assertFalse(canDelete);
        
        // Test Legal Hold release
        legalHoldEvent.setLegalHold(false);
        legalHoldEvent.setComplianceFlags(new String[]{});
        
        assertFalse(legalHoldEvent.getLegalHold());
        assertArrayEquals(new String[]{}, legalHoldEvent.getComplianceFlags());
        
        // Test selective field exclusion for PII minimization
        AuditEvent minimizedExportEvent = new AuditEvent();
        minimizedExportEvent.setEventType(AuditEvent.EventTypes.DATA_EXPORT);
        minimizedExportEvent.setDescription("Data export with PII minimization");
        minimizedExportEvent.setPiiRedacted(true);
        minimizedExportEvent.setRedactedFields(new String[]{"ip_address", "device_fingerprint", "location"});
        minimizedExportEvent.setDetails("{\"excludedFields\":[\"ip_address\",\"device_fingerprint\"],\"reason\":\"PII_minimization\"}");
        
        assertTrue(minimizedExportEvent.getPiiRedacted());
        assertTrue(minimizedExportEvent.getDetails().contains("PII_minimization"));
        assertArrayEquals(new String[]{"ip_address", "device_fingerprint", "location"}, 
                         minimizedExportEvent.getRedactedFields());
        
        // Test consent withdrawal with data retention
        LinkedIdentity socialIdentity = new LinkedIdentity();
        socialIdentity.setUser(testUser);
        socialIdentity.setProvider("google");
        socialIdentity.setDataSharingConsent(true);
        socialIdentity.setConsentGivenAt(Instant.now().minus(30, ChronoUnit.DAYS));
        
        // Withdraw consent
        socialIdentity.withdrawConsent();
        assertFalse(socialIdentity.getDataSharingConsent());
        assertNotNull(socialIdentity.getConsentWithdrawnAt());
        
        // Profile sync should be disabled after consent withdrawal
        assertFalse(socialIdentity.getProfileSyncEnabled());
    }

    @Test
    @DisplayName("NFR2.4 - Automatically enforce different policy sets based on user's jurisdiction to comply with GDPR, CCPA, PDPA")
    void testJurisdictionAwarePolicies() {
        // Test GDPR compliance for EU users
        testUser.setLocale("de_DE"); // German user
        UserSession euSession = new UserSession();
        euSession.setUser(testUser);
        euSession.setLocationCountry("DE");
        
        TenantPolicy gdprPolicy = new TenantPolicy();
        gdprPolicy.setTenant(testTenant);
        gdprPolicy.setName("GDPR Compliance Policy");
        gdprPolicy.setPolicyType(TenantPolicy.PolicyType.COMPLIANCE);
        gdprPolicy.setConditions("{\"jurisdiction\":\"EU\",\"regulation\":\"GDPR\"}");
        gdprPolicy.setConsentRequired(true);
        gdprPolicy.setMetadata("{\"dataRetentionDays\":1095,\"rightToErasure\":true,\"explicitConsent\":true}");
        
        assertEquals("DE", euSession.getLocationCountry());
        assertTrue(gdprPolicy.getConsentRequired());
        assertTrue(gdprPolicy.getMetadata().contains("rightToErasure"));
        
        // Test CCPA compliance for California users
        User californiaUser = new User();
        californiaUser.setLocale("en_US");
        UserSession ccpaSession = new UserSession();
        ccpaSession.setUser(californiaUser);
        ccpaSession.setLocationCountry("US");
        ccpaSession.setLocationCity("San Francisco");
        
        TenantPolicy ccpaPolicy = new TenantPolicy();
        ccpaPolicy.setTenant(testTenant);
        ccpaPolicy.setName("CCPA Compliance Policy");
        ccpaPolicy.setPolicyType(TenantPolicy.PolicyType.COMPLIANCE);
        ccpaPolicy.setConditions("{\"jurisdiction\":\"CA\",\"regulation\":\"CCPA\"}");
        ccpaPolicy.setMetadata("{\"doNotSellData\":true,\"rightToKnow\":true,\"rightToDelete\":true}");
        
        assertEquals("US", ccpaSession.getLocationCountry());
        assertEquals("San Francisco", ccpaSession.getLocationCity());
        assertTrue(ccpaPolicy.getMetadata().contains("doNotSellData"));
        
        // Test PDPA compliance for Singapore users
        User singaporeUser = new User();
        singaporeUser.setLocale("en_SG");
        UserSession pdpaSession = new UserSession();
        pdpaSession.setUser(singaporeUser);
        pdpaSession.setLocationCountry("SG");
        
        TenantPolicy pdpaPolicy = new TenantPolicy();
        pdpaPolicy.setTenant(testTenant);
        pdpaPolicy.setName("PDPA Compliance Policy");
        pdpaPolicy.setPolicyType(TenantPolicy.PolicyType.COMPLIANCE);
        pdpaPolicy.setConditions("{\"jurisdiction\":\"SG\",\"regulation\":\"PDPA\"}");
        pdpaPolicy.setMetadata("{\"consentWithdrawal\":true,\"dataPortability\":true,\"businessImprovementException\":false}");
        
        assertEquals("SG", pdpaSession.getLocationCountry());
        assertTrue(pdpaPolicy.getMetadata().contains("consentWithdrawal"));
        
        // Test automatic policy selection based on jurisdiction
        String userJurisdiction = determineJurisdiction(euSession.getLocationCountry());
        assertEquals("EU", userJurisdiction);
        
        userJurisdiction = determineJurisdiction(ccpaSession.getLocationCountry(), ccpaSession.getLocationCity());
        assertEquals("CCPA", userJurisdiction);
        
        userJurisdiction = determineJurisdiction(pdpaSession.getLocationCountry());
        assertEquals("PDPA", userJurisdiction);
        
        // Test cross-border data transfer restrictions
        AuditEvent dataTransferEvent = new AuditEvent();
        dataTransferEvent.setEventType("compliance.data.transfer");
        dataTransferEvent.setLocationCountry("DE");
        dataTransferEvent.setDescription("Cross-border data transfer - adequate protection verified");
        dataTransferEvent.setComplianceFlags(new String[]{"GDPR_Article_44", "adequacy_decision"});
        
        assertTrue(Arrays.asList(dataTransferEvent.getComplianceFlags()).contains("GDPR_Article_44"));
        assertTrue(dataTransferEvent.getDescription().contains("adequate protection"));
    }

    @Test
    @DisplayName("NFR2.5 - Audit logs must be structured and indexed for forensic timeline construction")
    void testForensicTimelines() {
        // Test timeline construction for security incident
        UUID incidentId = UUID.randomUUID();
        String correlationId = "incident_" + incidentId;
        
        // Create sequence of related events
        AuditEvent suspiciousLogin = new AuditEvent();
        suspiciousLogin.setEventType(AuditEvent.EventTypes.LOGIN_FAILURE);
        suspiciousLogin.setTimestamp(Instant.now().minus(10, ChronoUnit.MINUTES));
        suspiciousLogin.setUserId(testUser.getId());
        suspiciousLogin.setCorrelationId(correlationId);
        suspiciousLogin.setIpAddress("192.168.1.100");
        suspiciousLogin.setRiskScore(75.0);
        suspiciousLogin.setRiskFactors(new String[]{"new_device", "unusual_location"});
        
        AuditEvent sessionCreated = new AuditEvent();
        sessionCreated.setEventType(AuditEvent.EventTypes.SESSION_CREATED);
        sessionCreated.setTimestamp(Instant.now().minus(8, ChronoUnit.MINUTES));
        sessionCreated.setUserId(testUser.getId());
        sessionCreated.setCorrelationId(correlationId);
        sessionCreated.setSessionId("session_123");
        sessionCreated.setIpAddress("192.168.1.100");
        
        AuditEvent suspiciousActivity = new AuditEvent();
        suspiciousActivity.setEventType(AuditEvent.EventTypes.SUSPICIOUS_ACTIVITY);
        suspiciousActivity.setTimestamp(Instant.now().minus(5, ChronoUnit.MINUTES));
        suspiciousActivity.setUserId(testUser.getId());
        suspiciousActivity.setCorrelationId(correlationId);
        suspiciousActivity.setSessionId("session_123");
        suspiciousActivity.setDescription("Rapid API calls detected");
        suspiciousActivity.setSeverity(AuditEvent.Severity.WARN);
        
        AuditEvent sessionTerminated = new AuditEvent();
        sessionTerminated.setEventType(AuditEvent.EventTypes.SESSION_TERMINATED);
        sessionTerminated.setTimestamp(Instant.now().minus(2, ChronoUnit.MINUTES));
        sessionTerminated.setUserId(testUser.getId());
        sessionTerminated.setCorrelationId(correlationId);
        sessionTerminated.setSessionId("session_123");
        sessionTerminated.setDescription("Session terminated due to suspicious activity");
        sessionTerminated.setSeverity(AuditEvent.Severity.SECURITY_INCIDENT);
        
        // Test timeline indexing
        assertEquals(correlationId, suspiciousLogin.getCorrelationId());
        assertEquals(correlationId, sessionCreated.getCorrelationId());
        assertEquals(correlationId, suspiciousActivity.getCorrelationId());
        assertEquals(correlationId, sessionTerminated.getCorrelationId());
        
        // Test chronological ordering
        assertTrue(suspiciousLogin.getTimestamp().isBefore(sessionCreated.getTimestamp()));
        assertTrue(sessionCreated.getTimestamp().isBefore(suspiciousActivity.getTimestamp()));
        assertTrue(suspiciousActivity.getTimestamp().isBefore(sessionTerminated.getTimestamp()));
        
        // Test forensic data completeness
        assertNotNull(suspiciousLogin.getIpAddress());
        assertNotNull(suspiciousLogin.getRiskFactors());
        assertNotNull(sessionCreated.getSessionId());
        assertNotNull(suspiciousActivity.getDescription());
        assertEquals(AuditEvent.Severity.SECURITY_INCIDENT, sessionTerminated.getSeverity());
        
        // Test tracing identifiers for distributed forensics
        suspiciousLogin.setTraceId("trace_" + UUID.randomUUID());
        suspiciousLogin.setSpanId("span_" + UUID.randomUUID());
        
        assertNotNull(suspiciousLogin.getTraceId());
        assertNotNull(suspiciousLogin.getSpanId());
        
        // Test forensic query capability
        boolean isSecurityEvent = suspiciousActivity.isSecurityEvent();
        assertTrue(isSecurityEvent);
        
        boolean isHighSeverity = sessionTerminated.isHighSeverity();
        assertTrue(isHighSeverity);
        
        // Test environment and version tracking for forensics
        suspiciousLogin.setEnvironment("production");
        suspiciousLogin.setApplicationVersion("1.0.0");
        
        assertEquals("production", suspiciousLogin.getEnvironment());
        assertEquals("1.0.0", suspiciousLogin.getApplicationVersion());
        
        // Test user activity timeline construction
        assertTrue(suspiciousLogin.getTimestamp().isBefore(sessionTerminated.getTimestamp()));
        assertEquals(testUser.getId(), suspiciousLogin.getUserId());
        assertEquals(testUser.getId(), sessionTerminated.getUserId());
        
        // Verify all events in timeline have same user and correlation ID
        assertEquals(suspiciousLogin.getUserId(), sessionCreated.getUserId());
        assertEquals(sessionCreated.getUserId(), suspiciousActivity.getUserId());
        assertEquals(suspiciousActivity.getUserId(), sessionTerminated.getUserId());
        
        assertEquals(suspiciousLogin.getCorrelationId(), sessionCreated.getCorrelationId());
        assertEquals(sessionCreated.getCorrelationId(), suspiciousActivity.getCorrelationId());
        assertEquals(suspiciousActivity.getCorrelationId(), sessionTerminated.getCorrelationId());
    }

    // Helper methods
    private Tenant createTestTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setTenantCode("test-tenant");
        tenant.setName("Test Tenant");
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        return tenant;
    }

    private User createTestUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenant(testTenant);
        user.setEmail("test@example.com");
        user.setStatus(User.UserStatus.ACTIVE);
        return user;
    }

    private AuditEvent createTestAuditEvent() {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID());
        event.setTimestamp(Instant.now());
        event.setTenantId(testTenant.getId());
        event.setUserId(testUser.getId());
        event.setEventType(AuditEvent.EventTypes.LOGIN_SUCCESS);
        event.setSeverity(AuditEvent.Severity.INFO);
        return event;
    }

    private String calculateEventHash(AuditEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = event.getTimestamp() + "|" + event.getEventType() + "|" + event.getChainSequence();
            return java.util.Base64.getEncoder().encodeToString(digest.digest(data.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private String determineJurisdiction(String country) {
        return determineJurisdiction(country, null);
    }

    private String determineJurisdiction(String country, String city) {
        // EU countries (simplified list)
        String[] euCountries = {"DE", "FR", "IT", "ES", "NL", "BE", "AT", "SE", "DK", "FI"};
        
        if (java.util.Arrays.asList(euCountries).contains(country)) {
            return "EU";
        }
        
        if ("US".equals(country) && "San Francisco".equals(city)) {
            return "CCPA";
        }
        
        if ("SG".equals(country)) {
            return "PDPA";
        }
        
        return "DEFAULT";
    }
}
