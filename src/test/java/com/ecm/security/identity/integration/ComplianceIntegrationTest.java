package com.ecm.security.identity.integration;

import com.ecm.security.identity.domain.*;
import com.ecm.security.identity.repository.*;
import com.ecm.security.identity.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for NFR2 - Observability & Compliance requirements using Testcontainers.
 * These tests validate GDPR, CCPA, PDPA compliance and audit capabilities.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                classes = {TestWebConfig.class})
@Testcontainers
@DisplayName("NFR2 - Observability & Compliance Integration Tests")
class ComplianceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("ecm_identity_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "test_redis_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.data.redis.password", () -> "test_redis_pass");
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private UserSessionRepository sessionRepository;

    private String baseUrl;
    private Tenant testTenant;
    private User euUser;
    private User californiaUser;
    private User singaporeUser;
    private String euUserToken;
    private String californiaUserToken;
    private String singaporeUserToken;

    @BeforeEach
    @Transactional
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1";
        
        // Clean up test data
        auditEventRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
        
        // Create test tenant
        testTenant = new Tenant();
        testTenant.setTenantCode("compliance-tenant");
        testTenant.setName("Compliance Test Tenant");
        testTenant.setStatus(Tenant.TenantStatus.ACTIVE);
        testTenant = tenantRepository.save(testTenant);
        
        // Create EU user (GDPR jurisdiction)
        euUser = new User();
        euUser.setTenant(testTenant);
        euUser.setEmail("eu.user@example.com");
        euUser.setUsername("euuser");
        euUser.setStatus(User.UserStatus.ACTIVE);
        euUser.setLocale("de_DE");
        euUser.setDateOfBirth(LocalDate.of(1990, 1, 1));
        euUser.setTermsVersion("v2.1");
        euUser.setTermsAcceptedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        euUser.setPrivacyPolicyVersion("v1.5");
        euUser.setPrivacyPolicyAcceptedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        euUser = userRepository.save(euUser);
        
        // Create California user (CCPA jurisdiction)
        californiaUser = new User();
        californiaUser.setTenant(testTenant);
        californiaUser.setEmail("ca.user@example.com");
        californiaUser.setUsername("causer");
        californiaUser.setStatus(User.UserStatus.ACTIVE);
        californiaUser.setLocale("en_US");
        californiaUser.setDateOfBirth(LocalDate.of(1985, 5, 15));
        californiaUser.setTermsVersion("v2.1");
        californiaUser.setTermsAcceptedAt(Instant.now().minus(20, ChronoUnit.DAYS));
        californiaUser.setPrivacyPolicyVersion("v1.5");
        californiaUser.setPrivacyPolicyAcceptedAt(Instant.now().minus(20, ChronoUnit.DAYS));
        californiaUser = userRepository.save(californiaUser);
        
        // Create Singapore user (PDPA jurisdiction)
        singaporeUser = new User();
        singaporeUser.setTenant(testTenant);
        singaporeUser.setEmail("sg.user@example.com");
        singaporeUser.setUsername("sguser");
        singaporeUser.setStatus(User.UserStatus.ACTIVE);
        singaporeUser.setLocale("en_SG");
        singaporeUser.setDateOfBirth(LocalDate.of(1988, 8, 8));
        singaporeUser.setTermsVersion("v2.1");
        singaporeUser.setTermsAcceptedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        singaporeUser.setPrivacyPolicyVersion("v1.5");
        singaporeUser.setPrivacyPolicyAcceptedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        singaporeUser = userRepository.save(singaporeUser);
        
        // Get authentication tokens
        euUserToken = authenticateUser(euUser.getEmail(), "eu_password");
        californiaUserToken = authenticateUser(californiaUser.getEmail(), "ca_password");
        singaporeUserToken = authenticateUser(singaporeUser.getEmail(), "sg_password");
    }

    @Test
    @DisplayName("NFR2.1 - Generate cryptographically chained audit logs that are tamper-evident")
    void testCryptographicallyChainedAuditLogs() {
        // Create sequence of audit events
        AuditEvent firstEvent = new AuditEvent();
        firstEvent.setTenantId(testTenant.getId());
        firstEvent.setUserId(euUser.getId());
        firstEvent.setEventType(AuditEvent.EventTypes.LOGIN_SUCCESS);
        firstEvent.setSeverity(AuditEvent.Severity.INFO);
        firstEvent.setDescription("User logged in successfully");
        firstEvent.setChainSequence(1L);
        firstEvent.setPreviousEventHash(null);
        firstEvent.setEventHash("hash_1");
        firstEvent = auditEventRepository.save(firstEvent);
        
        AuditEvent secondEvent = new AuditEvent();
        secondEvent.setTenantId(testTenant.getId());
        secondEvent.setUserId(euUser.getId());
        secondEvent.setEventType(AuditEvent.EventTypes.DATA_EXPORT);
        secondEvent.setSeverity(AuditEvent.Severity.INFO);
        secondEvent.setDescription("User requested data export");
        secondEvent.setChainSequence(2L);
        secondEvent.setPreviousEventHash(firstEvent.getEventHash());
        secondEvent.setEventHash("hash_2");
        secondEvent = auditEventRepository.save(secondEvent);
        
        // Verify audit chain integrity
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(euUserToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map> chainResponse = restTemplate.exchange(
            baseUrl + "/audit/chain/verify",
            HttpMethod.GET,
            entity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, chainResponse.getStatusCode());
        Map<String, Object> chainResult = chainResponse.getBody();
        assertTrue((Boolean) chainResult.get("chainValid"));
        // Handle JSON deserialization which may convert Long to Integer
        assertEquals(2L, ((Number) chainResult.get("lastSequence")).longValue());
        
        // Test PII redaction in audit logs
        Map<String, Object> redactionRequest = Map.of(
            "eventId", secondEvent.getId().toString(),
            "redactFields", List.of("email", "ip_address")
        );
        
        HttpEntity<Map<String, Object>> redactionEntity = new HttpEntity<>(redactionRequest, headers);
        
        ResponseEntity<Map> redactionResponse = restTemplate.exchange(
            baseUrl + "/audit/redact",
            HttpMethod.POST,
            redactionEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, redactionResponse.getStatusCode());
        Map<String, Object> redactionResult = redactionResponse.getBody();
        assertTrue((Boolean) redactionResult.get("redactionApplied"));
        
        // Verify tamper detection
        Map<String, Object> tamperRequest = Map.of(
            "eventId", firstEvent.getId().toString(),
            "modifiedDescription", "Tampered description"
        );
        
        HttpEntity<Map<String, Object>> tamperEntity = new HttpEntity<>(tamperRequest, headers);
        
        ResponseEntity<Map> tamperResponse = restTemplate.exchange(
            baseUrl + "/audit/tamper-check",
            HttpMethod.POST,
            tamperEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, tamperResponse.getStatusCode());
        Map<String, Object> tamperResult = tamperResponse.getBody();
        assertFalse((Boolean) tamperResult.get("integrityValid"));
        assertNotNull(tamperResult.get("tamperEvidence"));
    }

    @Test
    @DisplayName("NFR2.2 - Persist specific version of Terms of Service and trigger re-consent flows")
    void testConsentAndToSVersioning() {
        // Test current consent status
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(euUserToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map> consentStatusResponse = restTemplate.exchange(
            baseUrl + "/privacy/consent/status",
            HttpMethod.GET,
            entity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, consentStatusResponse.getStatusCode());
        Map<String, Object> consentStatus = consentStatusResponse.getBody();
        assertEquals("v2.1", consentStatus.get("termsVersion"));
        assertEquals("v1.5", consentStatus.get("privacyPolicyVersion"));
        assertTrue((Boolean) consentStatus.get("consentCurrent"));
        
        // Update Terms of Service version (trigger re-consent)
        Map<String, Object> tosUpdateRequest = Map.of(
            "newTermsVersion", "v2.2",
            "newPrivacyPolicyVersion", "v1.6",
            "changes", List.of(
                "Updated data retention policies",
                "Added new cookie usage guidelines",
                "Clarified third-party data sharing"
            )
        );
        
        ResponseEntity<Map> tosUpdateResponse = restTemplate.postForEntity(
            baseUrl + "/admin/legal/update-versions",
            tosUpdateRequest,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, tosUpdateResponse.getStatusCode());
        
        // Check consent status after update
        ResponseEntity<Map> updatedConsentResponse = restTemplate.exchange(
            baseUrl + "/privacy/consent/status",
            HttpMethod.GET,
            entity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, updatedConsentResponse.getStatusCode());
        Map<String, Object> updatedConsent = updatedConsentResponse.getBody();
        assertFalse((Boolean) updatedConsent.get("consentCurrent"));
        assertEquals("RECONSENT_REQUIRED", updatedConsent.get("status"));
        
        // Provide new consent
        Map<String, Object> newConsentRequest = Map.of(
            "userId", euUser.getId().toString(),
            "termsVersion", "v2.2",
            "privacyPolicyVersion", "v1.6",
            "consentGiven", true,
            "marketingConsent", false,
            "dataProcessingConsent", true
        );
        
        HttpEntity<Map<String, Object>> newConsentEntity = new HttpEntity<>(newConsentRequest, headers);
        
        ResponseEntity<Map> newConsentResponse = restTemplate.exchange(
            baseUrl + "/privacy/consent",
            HttpMethod.POST,
            newConsentEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, newConsentResponse.getStatusCode());
        Map<String, Object> newConsentResult = newConsentResponse.getBody();
        assertEquals("CONSENT_UPDATED", newConsentResult.get("status"));
        
        // Verify consent audit trail
        var auditEvents = auditEventRepository.findByUserId(euUser.getId());
        boolean hasConsentEvent = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals(AuditEvent.EventTypes.CONSENT_GIVEN) &&
                              event.getDescription().contains("v2.2"));
        assertTrue(hasConsentEvent);
    }

    @Test
    @DisplayName("NFR2.3 - Right to Erasure that coexists with Legal Hold requirements")
    void testRightToErasureWithLegalHold() {
        // Request data deletion (Right to Erasure)
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(euUserToken);
        
        Map<String, Object> deletionRequest = Map.of(
            "reason", "User no longer wishes to use the service",
            "confirmEmail", euUser.getEmail()
        );
        
        HttpEntity<Map<String, Object>> deletionEntity = new HttpEntity<>(deletionRequest, headers);
        
        ResponseEntity<Map> deletionResponse = restTemplate.exchange(
            baseUrl + "/privacy/delete-account",
            HttpMethod.POST,
            deletionEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, deletionResponse.getStatusCode());
        Map<String, Object> deletionResult = deletionResponse.getBody();
        assertEquals("DELETION_SCHEDULED", deletionResult.get("status"));
        String deletionRequestId = (String) deletionResult.get("deletionRequestId");
        assertNotNull(deletionRequestId);
        
        // Simulate legal hold placement
        Map<String, Object> legalHoldRequest = Map.of(
            "userId", euUser.getId().toString(),
            "reason", "Regulatory investigation",
            "authority", "Data Protection Authority",
            "caseNumber", "CASE-2024-001",
            "holdType", "LITIGATION_HOLD"
        );
        
        ResponseEntity<Map> legalHoldResponse = restTemplate.postForEntity(
            baseUrl + "/admin/legal/hold",
            legalHoldRequest,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, legalHoldResponse.getStatusCode());
        Map<String, Object> holdResult = legalHoldResponse.getBody();
        assertEquals("LEGAL_HOLD_APPLIED", holdResult.get("status"));
        
        // Check deletion status with legal hold
        ResponseEntity<Map> deletionStatusResponse = restTemplate.exchange(
            baseUrl + "/privacy/deletion-status/" + deletionRequestId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, deletionStatusResponse.getStatusCode());
        Map<String, Object> statusResult = deletionStatusResponse.getBody();
        assertEquals("DELETION_SUSPENDED", statusResult.get("status"));
        assertEquals("LEGAL_HOLD_ACTIVE", statusResult.get("suspensionReason"));
        
        // Release legal hold
        Map<String, Object> holdReleaseRequest = Map.of(
            "userId", euUser.getId().toString(),
            "releaseReason", "Investigation completed",
            "releaseAuthority", "Data Protection Authority"
        );
        
        ResponseEntity<Map> holdReleaseResponse = restTemplate.postForEntity(
            baseUrl + "/admin/legal/hold/release",
            holdReleaseRequest,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, holdReleaseResponse.getStatusCode());
        
        // Check final deletion status
        ResponseEntity<Map> finalStatusResponse = restTemplate.exchange(
            baseUrl + "/privacy/deletion-status/" + deletionRequestId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, finalStatusResponse.getStatusCode());
        Map<String, Object> finalStatus = finalStatusResponse.getBody();
        assertEquals("DELETION_PROCEEDING", finalStatus.get("status"));
    }

    @Test
    @DisplayName("NFR2.4 - Automatically enforce different policy sets based on user's jurisdiction")
    void testJurisdictionAwarePolicies() {
        // Test GDPR compliance for EU user
        HttpHeaders euHeaders = new HttpHeaders();
        euHeaders.setBearerAuth(euUserToken);
        HttpEntity<Void> euEntity = new HttpEntity<>(euHeaders);
        
        ResponseEntity<Map> gdprPolicyResponse = restTemplate.exchange(
            baseUrl + "/privacy/applicable-policies",
            HttpMethod.GET,
            euEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, gdprPolicyResponse.getStatusCode());
        Map<String, Object> gdprPolicies = gdprPolicyResponse.getBody();
        assertEquals("GDPR", gdprPolicies.get("primaryRegulation"));
        assertTrue((Boolean) gdprPolicies.get("rightToErasure"));
        assertTrue((Boolean) gdprPolicies.get("dataPortability"));
        assertTrue((Boolean) gdprPolicies.get("explicitConsent"));
        assertEquals(1095, gdprPolicies.get("dataRetentionDays")); // 3 years
        
        // Test CCPA compliance for California user
        HttpHeaders caHeaders = new HttpHeaders();
        caHeaders.setBearerAuth(californiaUserToken);
        HttpEntity<Void> caEntity = new HttpEntity<>(caHeaders);
        
        ResponseEntity<Map> ccpaPolicyResponse = restTemplate.exchange(
            baseUrl + "/privacy/applicable-policies",
            HttpMethod.GET,
            caEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, ccpaPolicyResponse.getStatusCode());
        Map<String, Object> ccpaPolicies = ccpaPolicyResponse.getBody();
        assertEquals("CCPA", ccpaPolicies.get("primaryRegulation"));
        assertTrue((Boolean) ccpaPolicies.get("rightToKnow"));
        assertTrue((Boolean) ccpaPolicies.get("rightToDelete"));
        assertTrue((Boolean) ccpaPolicies.get("doNotSellMyData"));
        assertFalse((Boolean) ccpaPolicies.get("explicitConsent")); // Opt-out model
        
        // Test PDPA compliance for Singapore user
        HttpHeaders sgHeaders = new HttpHeaders();
        sgHeaders.setBearerAuth(singaporeUserToken);
        HttpEntity<Void> sgEntity = new HttpEntity<>(sgHeaders);
        
        ResponseEntity<Map> pdpaPolicyResponse = restTemplate.exchange(
            baseUrl + "/privacy/applicable-policies",
            HttpMethod.GET,
            sgEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, pdpaPolicyResponse.getStatusCode());
        Map<String, Object> pdpaPolicies = pdpaPolicyResponse.getBody();
        assertEquals("PDPA", pdpaPolicies.get("primaryRegulation"));
        assertTrue((Boolean) pdpaPolicies.get("consentWithdrawal"));
        assertTrue((Boolean) pdpaPolicies.get("dataPortability"));
        assertFalse((Boolean) pdpaPolicies.get("businessImprovementException"));
        
        // Test CCPA "Do Not Sell" request
        Map<String, Object> doNotSellRequest = Map.of(
            "doNotSell", true,
            "reason", "User requested to opt out of data sales"
        );
        
        HttpEntity<Map<String, Object>> doNotSellEntity = new HttpEntity<>(doNotSellRequest, caHeaders);
        
        ResponseEntity<Map> doNotSellResponse = restTemplate.exchange(
            baseUrl + "/privacy/ccpa/do-not-sell",
            HttpMethod.POST,
            doNotSellEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, doNotSellResponse.getStatusCode());
        Map<String, Object> doNotSellResult = doNotSellResponse.getBody();
        assertEquals("DO_NOT_SELL_ACTIVATED", doNotSellResult.get("status"));
        
        // Test jurisdiction-specific data export formats
        ResponseEntity<Map> euExportResponse = restTemplate.exchange(
            baseUrl + "/privacy/export",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("format", "GDPR_COMPLIANT"), euHeaders),
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, euExportResponse.getStatusCode());
        Map<String, Object> euExportResult = euExportResponse.getBody();
        assertTrue(euExportResult.get("format").toString().contains("GDPR"));
        assertTrue((Boolean) euExportResult.get("cryptographicallySigned"));
    }

    @Test
    @DisplayName("NFR2.5 - Audit logs structured for forensic timeline construction")
    void testForensicTimelines() {
        // Create a sequence of security-related events
        String correlationId = "security_incident_" + UUID.randomUUID();
        
        // Login event
        createTestAuditEvent(euUser.getId(), AuditEvent.EventTypes.LOGIN_SUCCESS, 
            "User logged in from new device", correlationId, -10);
        
        // Suspicious activity
        createTestAuditEvent(euUser.getId(), AuditEvent.EventTypes.SUSPICIOUS_ACTIVITY, 
            "Multiple API calls from different locations", correlationId, -8);
        
        // Risk escalation
        createTestAuditEvent(euUser.getId(), "security.risk.escalated", 
            "Risk score increased to 85", correlationId, -5);
        
        // Session termination
        createTestAuditEvent(euUser.getId(), AuditEvent.EventTypes.SESSION_TERMINATED, 
            "Session terminated due to high risk", correlationId, -2);
        
        // Forensic timeline request
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(euUserToken);
        
        Map<String, Object> timelineRequest = Map.of(
            "userId", euUser.getId().toString(),
            "correlationId", correlationId,
            "timeRange", Map.of(
                "start", Instant.now().minus(15, ChronoUnit.MINUTES).toString(),
                "end", Instant.now().toString()
            ),
            "includeContext", true
        );
        
        HttpEntity<Map<String, Object>> timelineEntity = new HttpEntity<>(timelineRequest, headers);
        
        ResponseEntity<Map> timelineResponse = restTemplate.exchange(
            baseUrl + "/audit/forensic/timeline",
            HttpMethod.POST,
            timelineEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, timelineResponse.getStatusCode());
        Map<String, Object> timelineResult = timelineResponse.getBody();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> events = (List<Map<String, Object>>) timelineResult.get("events");
        assertEquals(4, events.size());
        
        // Verify chronological ordering
        for (int i = 1; i < events.size(); i++) {
            String prevTimestamp = (String) events.get(i-1).get("timestamp");
            String currTimestamp = (String) events.get(i).get("timestamp");
            assertTrue(Instant.parse(prevTimestamp).isBefore(Instant.parse(currTimestamp)));
        }
        
        // Verify correlation ID consistency
        events.forEach(event -> assertEquals(correlationId, event.get("correlationId")));
        
        // Test security incident correlation
        Map<String, Object> incidentRequest = Map.of(
            "incidentType", "SUSPICIOUS_ACTIVITY",
            "correlationId", correlationId,
            "severity", "HIGH"
        );
        
        HttpEntity<Map<String, Object>> incidentEntity = new HttpEntity<>(incidentRequest, headers);
        
        ResponseEntity<Map> incidentResponse = restTemplate.exchange(
            baseUrl + "/audit/forensic/incident",
            HttpMethod.POST,
            incidentEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, incidentResponse.getStatusCode());
        Map<String, Object> incidentResult = incidentResponse.getBody();
        assertEquals("INCIDENT_CREATED", incidentResult.get("status"));
        assertNotNull(incidentResult.get("incidentId"));
        
        // Verify incident links to timeline events
        assertTrue((Boolean) incidentResult.get("timelineLinked"));
        assertEquals(4, incidentResult.get("relatedEventCount"));
    }

    @Test
    @DisplayName("Complete compliance flow: Data export with jurisdiction-specific requirements")
    void testCompleteComplianceDataExportFlow() {
        // EU user requests data export (GDPR Article 20)
        HttpHeaders euHeaders = new HttpHeaders();
        euHeaders.setBearerAuth(euUserToken);
        
        Map<String, Object> exportRequest = Map.of(
            "format", "JSON",
            "includeAuditLogs", true,
            "includeSessions", true,
            "includeCredentials", false, // Exclude for security
            "piiMinimization", true,
            "digitalSignature", true
        );
        
        HttpEntity<Map<String, Object>> exportEntity = new HttpEntity<>(exportRequest, euHeaders);
        
        ResponseEntity<Map> exportResponse = restTemplate.exchange(
            baseUrl + "/privacy/export",
            HttpMethod.POST,
            exportEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, exportResponse.getStatusCode());
        Map<String, Object> exportResult = exportResponse.getBody();
        assertEquals("EXPORT_INITIATED", exportResult.get("status"));
        String exportId = (String) exportResult.get("exportId");
        assertNotNull(exportId);
        
        // Check export status
        ResponseEntity<Map> statusResponse = restTemplate.exchange(
            baseUrl + "/privacy/export/" + exportId + "/status",
            HttpMethod.GET,
            new HttpEntity<>(euHeaders),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        Map<String, Object> statusResult = statusResponse.getBody();
        assertEquals("PROCESSING", statusResult.get("status"));
        
        // Simulate export completion and retrieve
        ResponseEntity<Map> downloadResponse = restTemplate.exchange(
            baseUrl + "/privacy/export/" + exportId + "/download",
            HttpMethod.GET,
            new HttpEntity<>(euHeaders),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, downloadResponse.getStatusCode());
        Map<String, Object> downloadResult = downloadResponse.getBody();
        
        // Verify GDPR compliance requirements
        assertNotNull(downloadResult.get("integrityHash"));
        assertNotNull(downloadResult.get("digitalSignature"));
        assertTrue((Boolean) downloadResult.get("piiMinimized"));
        assertEquals("GDPR", downloadResult.get("complianceFramework"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> exportData = (Map<String, Object>) downloadResult.get("data");
        assertNotNull(exportData.get("profile"));
        assertNotNull(exportData.get("auditEvents"));
        assertNotNull(exportData.get("sessions"));
        assertNull(exportData.get("credentials")); // Should be excluded for security
        
        // Verify audit trail for export
        var auditEvents = auditEventRepository.findByUserId(euUser.getId());
        boolean hasExportEvent = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals(AuditEvent.EventTypes.DATA_EXPORT) &&
                              event.getDescription().contains(exportId));
        assertTrue(hasExportEvent);
    }

    private void createTestAuditEvent(UUID userId, String eventType, String description, 
                                      String correlationId, int minutesOffset) {
        AuditEvent event = new AuditEvent();
        event.setTenantId(testTenant.getId());
        event.setUserId(userId);
        event.setEventType(eventType);
        event.setSeverity(AuditEvent.Severity.INFO);
        event.setDescription(description);
        event.setCorrelationId(correlationId);
        event.setTimestamp(Instant.now().plusSeconds(minutesOffset * 60));
        event.setIpAddress("192.168.1.100");
        event.setUserAgent("TestAgent/1.0");
        auditEventRepository.save(event);
    }

    private String authenticateUser(String email, String password) {
        Map<String, Object> loginRequest = Map.of(
            "email", email,
            "password", password
        );
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            loginRequest,
            Map.class
        );
        
        if (response.getStatusCode() == HttpStatus.OK) {
            return (String) response.getBody().get("accessToken");
        }
        return null;
    }
}
