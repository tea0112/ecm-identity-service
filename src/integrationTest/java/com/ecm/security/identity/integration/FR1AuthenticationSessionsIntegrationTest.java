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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for FR1 - Authentication & Sessions requirements.
 * These tests validate all authentication flows, session management, and security features.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Transactional
class FR1AuthenticationSessionsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("ecm_identity_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private UserCredentialRepository credentialRepository;

    @Autowired
    private UserDeviceRepository deviceRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private Tenant testTenant;
    private User testUser;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        
        // Create test tenant
        testTenant = Tenant.builder()
                .tenantCode("test-tenant")
                .name("Test Tenant")
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepository.save(testTenant);

        // Create test user
        testUser = User.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .tenant(testTenant)
                .status(User.UserStatus.ACTIVE)
                .build();
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("FR1.1 - Credential Login & Passwordless Authentication")
    void testCredentialLoginAndPasswordlessAuth() throws Exception {
        // Test password authentication
        Map<String, Object> passwordLoginRequest = Map.of(
                "email", testUser.getEmail(),
                "password", "SecurePassword123!",
                "tenantCode", testTenant.getTenantCode()
        );

        ResponseEntity<Map> passwordResponse = restTemplate.postForEntity(
                baseUrl + "/auth/login/password",
                passwordLoginRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, passwordResponse.getStatusCode());
        Map<String, Object> passwordResult = passwordResponse.getBody();
        assertNotNull(passwordResult.get("accessToken"));
        assertNotNull(passwordResult.get("refreshToken"));

        // Test magic link authentication
        Map<String, Object> magicLinkRequest = Map.of(
                "email", testUser.getEmail(),
                "tenantCode", testTenant.getTenantCode(),
                "redirectUri", "https://app.example.com/auth/callback"
        );

        ResponseEntity<Map> magicLinkResponse = restTemplate.postForEntity(
                baseUrl + "/auth/magic-link/request",
                magicLinkRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, magicLinkResponse.getStatusCode());
        Map<String, Object> magicLinkResult = magicLinkResponse.getBody();
        assertNotNull(magicLinkResult.get("linkId"));
        assertTrue((Boolean) magicLinkResult.get("sent"));

        // Test WebAuthn/passkey registration
        Map<String, Object> webauthnRegisterRequest = Map.of(
                "userId", testUser.getId().toString(),
                "displayName", testUser.getFirstName() + " " + testUser.getLastName(),
                "authenticatorType", "platform"
        );

        ResponseEntity<Map> webauthnResponse = restTemplate.postForEntity(
                baseUrl + "/auth/webauthn/register/begin",
                webauthnRegisterRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, webauthnResponse.getStatusCode());
        Map<String, Object> webauthnResult = webauthnResponse.getBody();
        assertNotNull(webauthnResult.get("challenge"));
        assertNotNull(webauthnResult.get("publicKeyCredentialCreationOptions"));

        // Verify audit events were created
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.password.attempt")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.magic_link.requested")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.webauthn.register.begin")));
    }

    @Test
    @DisplayName("FR1.2 - Token Issuance & Refresh with Rotation")
    void testTokenIssuanceAndRefresh() throws Exception {
        // Authenticate user to get initial tokens
        String accessToken = authenticateUser(testUser.getEmail(), "SecurePassword123!");

        // Test token refresh
        Map<String, Object> refreshRequest = Map.of(
                "refreshToken", "mock-refresh-token-" + testUser.getId(),
                "tenantCode", testTenant.getTenantCode()
        );

        ResponseEntity<Map> refreshResponse = restTemplate.postForEntity(
                baseUrl + "/auth/token/refresh",
                refreshRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode());
        Map<String, Object> refreshResult = refreshResponse.getBody();
        
        // Verify new tokens are issued
        assertNotNull(refreshResult.get("accessToken"));
        assertNotNull(refreshResult.get("refreshToken"));
        assertNotEquals(accessToken, refreshResult.get("accessToken"));

        // Verify token properties
        assertTrue(((String) refreshResult.get("accessToken")).length() > 100);
        assertEquals("Bearer", refreshResult.get("tokenType"));
        assertTrue((Integer) refreshResult.get("expiresIn") > 0);

        // Test refresh token rotation - old refresh token should be invalidated
        ResponseEntity<Map> oldRefreshResponse = restTemplate.postForEntity(
                baseUrl + "/auth/token/refresh",
                refreshRequest,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, oldRefreshResponse.getStatusCode());

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.token.refresh")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.refresh_token.rotated")));
    }

    @Test
    @DisplayName("FR1.3 - Multi-Factor & Step-Up Authentication")
    void testMultiFactorAndStepUpAuthentication() throws Exception {
        // Set up TOTP credential for user
        UserCredential totpCredential = UserCredential.builder()
                .user(testUser)
                .credentialType(UserCredential.CredentialType.TOTP)
                .credentialData("JBSWY3DPEHPK3PXP") // Base32 encoded secret
                .status(UserCredential.CredentialStatus.ACTIVE)
                .build();
        credentialRepository.save(totpCredential);

        // Test MFA enrollment
        Map<String, Object> mfaEnrollRequest = Map.of(
                "userId", testUser.getId().toString(),
                "mfaType", "totp",
                "deviceName", "Test Device"
        );

        ResponseEntity<Map> mfaEnrollResponse = restTemplate.postForEntity(
                baseUrl + "/auth/mfa/enroll",
                mfaEnrollRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, mfaEnrollResponse.getStatusCode());
        Map<String, Object> mfaEnrollResult = mfaEnrollResponse.getBody();
        assertNotNull(mfaEnrollResult.get("secret"));
        assertNotNull(mfaEnrollResult.get("qrCode"));

        // Test MFA verification
        Map<String, Object> mfaVerifyRequest = Map.of(
                "userId", testUser.getId().toString(),
                "mfaType", "totp",
                "code", "123456", // Mock TOTP code
                "challengeId", UUID.randomUUID().toString()
        );

        ResponseEntity<Map> mfaVerifyResponse = restTemplate.postForEntity(
                baseUrl + "/auth/mfa/verify",
                mfaVerifyRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, mfaVerifyResponse.getStatusCode());
        Map<String, Object> mfaVerifyResult = mfaVerifyResponse.getBody();
        assertTrue((Boolean) mfaVerifyResult.get("verified"));

        // Test step-up authentication for high-risk operation
        String accessToken = authenticateUser(testUser.getEmail(), "SecurePassword123!");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("X-Risk-Level", "HIGH");

        ResponseEntity<Map> stepUpResponse = restTemplate.exchange(
                baseUrl + "/auth/step-up/required",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("operation", "delete_account"), headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, stepUpResponse.getStatusCode());
        Map<String, Object> stepUpResult = stepUpResponse.getBody();
        assertTrue((Boolean) stepUpResult.get("stepUpRequired"));
        assertNotNull(stepUpResult.get("challengeId"));
        assertEquals("mfa_required", stepUpResult.get("challengeType"));

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.mfa.enrolled")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.mfa.verified")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.step_up.required")));
    }

    @Test
    @DisplayName("FR1.4 - Device & Session Management")
    void testDeviceAndSessionManagement() throws Exception {
        // Create test device
        UserDevice testDevice = UserDevice.builder()
                .user(testUser)
                .deviceFingerprint("device-123-fingerprint")
                .deviceName("Test iPhone")
                .deviceType("mobile")
                .platform("iOS")
                .lastSeenAt(Instant.now())
                .build();
        deviceRepository.save(testDevice);

        // Create test session
        UserSession testSession = UserSession.builder()
                .user(testUser)
                .device(testDevice)
                .sessionId("session-123")
                .status(UserSession.SessionStatus.ACTIVE)
                .ipAddress("192.168.1.100")
                .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .lastActivityAt(Instant.now())
                .build();
        sessionRepository.save(testSession);

        String accessToken = authenticateUser(testUser.getEmail(), "SecurePassword123!");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // Test session listing
        ResponseEntity<Map> sessionsResponse = restTemplate.exchange(
                baseUrl + "/user/sessions",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, sessionsResponse.getStatusCode());
        Map<String, Object> sessionsResult = sessionsResponse.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) sessionsResult.get("sessions");
        assertTrue(sessions.size() > 0);

        Map<String, Object> session = sessions.get(0);
        assertNotNull(session.get("sessionId"));
        assertNotNull(session.get("deviceName"));
        assertNotNull(session.get("ipAddress"));
        assertNotNull(session.get("lastActivity"));
        assertEquals("active", session.get("status"));

        // Test session revocation
        String sessionIdToRevoke = (String) session.get("sessionId");
        ResponseEntity<Map> revokeResponse = restTemplate.exchange(
                baseUrl + "/user/sessions/" + sessionIdToRevoke + "/revoke",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, revokeResponse.getStatusCode());
        Map<String, Object> revokeResult = revokeResponse.getBody();
        assertTrue((Boolean) revokeResult.get("revoked"));

        // Test device binding verification
        Map<String, Object> deviceBindingRequest = Map.of(
                "sessionId", testSession.getSessionId(),
                "deviceFingerprint", Map.of(
                        "screen", Map.of("width", 375, "height", 667),
                        "timezone", "America/New_York",
                        "language", "en-US"
                ),
                "attestationData", "mock-attestation-data"
        );

        ResponseEntity<Map> bindingResponse = restTemplate.exchange(
                baseUrl + "/auth/device/verify-binding",
                HttpMethod.POST,
                new HttpEntity<>(deviceBindingRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, bindingResponse.getStatusCode());
        Map<String, Object> bindingResult = bindingResponse.getBody();
        assertTrue((Boolean) bindingResult.get("verified"));
        assertNotNull(bindingResult.get("trustScore"));

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("session.revoked")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("device.binding.verified")));
    }

    @Test
    @DisplayName("FR1.5 - Secure Account Recovery & Fallbacks")
    void testSecureAccountRecoveryAndFallbacks() throws Exception {
        // Test account recovery initiation
        Map<String, Object> recoveryRequest = Map.of(
                "email", testUser.getEmail(),
                "tenantCode", testTenant.getTenantCode(),
                "recoveryType", "email"
        );

        ResponseEntity<Map> recoveryResponse = restTemplate.postForEntity(
                baseUrl + "/auth/recovery/initiate",
                recoveryRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, recoveryResponse.getStatusCode());
        Map<String, Object> recoveryResult = recoveryResponse.getBody();
        assertNotNull(recoveryResult.get("recoveryId"));
        assertTrue((Boolean) recoveryResult.get("initiated"));
        assertNotNull(recoveryResult.get("expiresAt"));

        // Test high-assurance recovery channel
        Map<String, Object> highAssuranceRequest = Map.of(
                "email", testUser.getEmail(),
                "tenantCode", testTenant.getTenantCode(),
                "recoveryType", "hardware_token",
                "tokenSerial", "HW-TOKEN-123456"
        );

        ResponseEntity<Map> highAssuranceResponse = restTemplate.postForEntity(
                baseUrl + "/auth/recovery/high-assurance",
                highAssuranceRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, highAssuranceResponse.getStatusCode());
        Map<String, Object> highAssuranceResult = highAssuranceResponse.getBody();
        assertNotNull(highAssuranceResult.get("challengeId"));
        assertEquals("hardware_token", highAssuranceResult.get("challengeType"));

        // Test rate limiting on recovery attempts
        for (int i = 0; i < 6; i++) {
            restTemplate.postForEntity(
                    baseUrl + "/auth/recovery/initiate",
                    recoveryRequest,
                    Map.class
            );
        }

        ResponseEntity<Map> rateLimitedResponse = restTemplate.postForEntity(
                baseUrl + "/auth/recovery/initiate",
                recoveryRequest,
                Map.class
        );

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rateLimitedResponse.getStatusCode());

        // Test passwordless fallback
        Map<String, Object> passwordlessFallbackRequest = Map.of(
                "email", testUser.getEmail(),
                "tenantCode", testTenant.getTenantCode(),
                "fallbackMethod", "sms",
                "phoneNumber", "+1234567890"
        );

        ResponseEntity<Map> fallbackResponse = restTemplate.postForEntity(
                baseUrl + "/auth/passwordless/fallback",
                passwordlessFallbackRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, fallbackResponse.getStatusCode());
        Map<String, Object> fallbackResult = fallbackResponse.getBody();
        assertNotNull(fallbackResult.get("challengeId"));
        assertEquals("sms", fallbackResult.get("method"));

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.recovery.initiated")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.recovery.rate_limited")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.passwordless.fallback")));
    }

    @Test
    @DisplayName("FR1.6 - Account Linking & Merging")
    void testAccountLinkingAndMerging() throws Exception {
        // Create another user identity to link
        User socialUser = User.builder()
                .email("test.social@example.com")
                .firstName("Test")
                .lastName("Social")
                .tenant(testTenant)
                .status(User.UserStatus.ACTIVE)
                .build();
        socialUser = userRepository.save(socialUser);

        String accessToken = authenticateUser(testUser.getEmail(), "SecurePassword123!");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // Test account linking initiation
        Map<String, Object> linkingRequest = Map.of(
                "primaryUserId", testUser.getId().toString(),
                "secondaryUserId", socialUser.getId().toString(),
                "linkingType", "social",
                "provider", "google",
                "justification", "User wants to link their Google account"
        );

        ResponseEntity<Map> linkingResponse = restTemplate.exchange(
                baseUrl + "/user/accounts/link",
                HttpMethod.POST,
                new HttpEntity<>(linkingRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, linkingResponse.getStatusCode());
        Map<String, Object> linkingResult = linkingResponse.getBody();
        assertNotNull(linkingResult.get("linkingId"));
        assertEquals("pending", linkingResult.get("status"));
        assertNotNull(linkingResult.get("auditTrail"));

        // Test account merge with full audit trail
        Map<String, Object> mergeRequest = Map.of(
                "primaryUserId", testUser.getId().toString(),
                "secondaryUserId", socialUser.getId().toString(),
                "mergeStrategy", "preserve_primary",
                "dataMapping", Map.of(
                        "permissions", "merge",
                        "preferences", "primary_wins",
                        "audit_history", "preserve_both"
                )
        );

        ResponseEntity<Map> mergeResponse = restTemplate.exchange(
                baseUrl + "/user/accounts/merge",
                HttpMethod.POST,
                new HttpEntity<>(mergeRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, mergeResponse.getStatusCode());
        Map<String, Object> mergeResult = mergeResponse.getBody();
        assertNotNull(mergeResult.get("mergeId"));
        assertEquals("completed", mergeResult.get("status"));
        assertNotNull(mergeResult.get("reversalToken"));

        // Test merge reversal capability
        String reversalToken = (String) mergeResult.get("reversalToken");
        Map<String, Object> reversalRequest = Map.of(
                "mergeId", mergeResult.get("mergeId"),
                "reversalToken", reversalToken,
                "reason", "User requested account separation"
        );

        ResponseEntity<Map> reversalResponse = restTemplate.exchange(
                baseUrl + "/user/accounts/merge/reverse",
                HttpMethod.POST,
                new HttpEntity<>(reversalRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, reversalResponse.getStatusCode());
        Map<String, Object> reversalResult = reversalResponse.getBody();
        assertTrue((Boolean) reversalResult.get("reversed"));
        assertNotNull(reversalResult.get("restoredAccounts"));

        // Verify comprehensive audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("account.linking.initiated")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("account.merge.completed")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("account.merge.reversed")));
    }

    @Test
    @DisplayName("FR1.7 - Age Gates & Parental Consent")
    void testAgeGatesAndParentalConsent() throws Exception {
        // Create a minor user
        User minorUser = User.builder()
                .email("minor@example.com")
                .firstName("Minor")
                .lastName("User")
                .tenant(testTenant)
                .status(User.UserStatus.PENDING_VERIFICATION)
                .build();
        minorUser = userRepository.save(minorUser);

        // Test age verification
        Map<String, Object> ageVerificationRequest = Map.of(
                "userId", minorUser.getId().toString(),
                "dateOfBirth", "2012-01-01",
                "jurisdiction", "US",
                "verificationMethod", "self_declaration"
        );

        ResponseEntity<Map> ageVerificationResponse = restTemplate.postForEntity(
                baseUrl + "/auth/age-verification",
                ageVerificationRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, ageVerificationResponse.getStatusCode());
        Map<String, Object> ageVerificationResult = ageVerificationResponse.getBody();
        assertTrue((Boolean) ageVerificationResult.get("isMinor"));
        assertTrue((Boolean) ageVerificationResult.get("requiresParentalConsent"));
        assertEquals("COPPA", ageVerificationResult.get("applicableRegulation"));

        // Test parental consent initiation
        Map<String, Object> parentalConsentRequest = Map.of(
                "minorUserId", minorUser.getId().toString(),
                "parentEmail", "parent@example.com",
                "parentName", "Parent Guardian",
                "relationshipType", "parent",
                "consentType", "registration_and_data_collection"
        );

        ResponseEntity<Map> consentResponse = restTemplate.postForEntity(
                baseUrl + "/auth/parental-consent/initiate",
                parentalConsentRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, consentResponse.getStatusCode());
        Map<String, Object> consentResult = consentResponse.getBody();
        assertNotNull(consentResult.get("consentId"));
        assertEquals("pending", consentResult.get("status"));
        assertNotNull(consentResult.get("verificationCode"));

        // Test parental consent verification
        Map<String, Object> consentVerificationRequest = Map.of(
                "consentId", consentResult.get("consentId"),
                "verificationCode", consentResult.get("verificationCode"),
                "parentSignature", "Parent Guardian",
                "consentDate", Instant.now().toString(),
                "ipAddress", "192.168.1.100"
        );

        ResponseEntity<Map> consentVerificationResponse = restTemplate.postForEntity(
                baseUrl + "/auth/parental-consent/verify",
                consentVerificationRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, consentVerificationResponse.getStatusCode());
        Map<String, Object> consentVerificationResult = consentVerificationResponse.getBody();
        assertTrue((Boolean) consentVerificationResult.get("verified"));
        assertEquals("active", consentVerificationResult.get("consentStatus"));

        // Verify audit events include parental consent tracking
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(minorUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("age.verification.completed") &&
                Arrays.asList(event.getComplianceFlags()).contains("COPPA")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("parental.consent.verified")));
    }

    @Test
    @DisplayName("FR1.8 - Proactive Session Invalidation based on Risk Signals")
    void testProactiveSessionInvalidationOnRiskSignals() throws Exception {
        // Create active session
        UserDevice testDevice = UserDevice.builder()
                .user(testUser)
                .deviceFingerprint("device-456-fingerprint")
                .deviceName("Test Device")
                .deviceType("desktop")
                .platform("Windows")
                .lastSeenAt(Instant.now())
                .build();
        deviceRepository.save(testDevice);

        UserSession activeSession = UserSession.builder()
                .user(testUser)
                .device(testDevice)
                .sessionId("session-456")
                .status(UserSession.SessionStatus.ACTIVE)
                .ipAddress("192.168.1.100")
                .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .lastActivityAt(Instant.now())
                .riskScore(25.0)
                .build();
        sessionRepository.save(activeSession);

        // Test impossible travel detection
        Map<String, Object> impossibleTravelSignal = Map.of(
                "userId", testUser.getId().toString(),
                "sessionId", activeSession.getSessionId(),
                "newLocation", Map.of(
                        "country", "JP",
                        "city", "Tokyo",
                        "coordinates", Map.of("lat", 35.6762, "lng", 139.6503)
                ),
                "previousLocation", Map.of(
                        "country", "US",
                        "city", "New York",
                        "coordinates", Map.of("lat", 40.7128, "lng", -74.0060)
                ),
                "timeDifference", "PT30M", // 30 minutes
                "ipAddress", "203.0.113.1"
        );

        ResponseEntity<Map> riskSignalResponse = restTemplate.postForEntity(
                baseUrl + "/auth/risk-signals/impossible-travel",
                impossibleTravelSignal,
                Map.class
        );

        assertEquals(HttpStatus.OK, riskSignalResponse.getStatusCode());
        Map<String, Object> riskResult = riskSignalResponse.getBody();
        assertTrue((Boolean) riskResult.get("riskDetected"));
        assertEquals("impossible_travel", riskResult.get("riskType"));
        assertTrue((Double) riskResult.get("riskScore") > 80.0);
        assertTrue((Boolean) riskResult.get("sessionInvalidated"));

        // Test anomalous device fingerprint detection
        Map<String, Object> deviceAnomalySignal = Map.of(
                "userId", testUser.getId().toString(),
                "sessionId", activeSession.getSessionId(),
                "deviceFingerprint", Map.of(
                        "screen", Map.of("width", 1920, "height", 1080),
                        "timezone", "Asia/Tokyo", // Changed from previous
                        "language", "ja-JP", // Changed from previous
                        "plugins", Arrays.asList("different-plugin-set")
                ),
                "previousFingerprint", Map.of(
                        "screen", Map.of("width", 1366, "height", 768),
                        "timezone", "America/New_York",
                        "language", "en-US",
                        "plugins", Arrays.asList("normal-plugin-set")
                )
        );

        ResponseEntity<Map> deviceAnomalyResponse = restTemplate.postForEntity(
                baseUrl + "/auth/risk-signals/device-anomaly",
                deviceAnomalySignal,
                Map.class
        );

        assertEquals(HttpStatus.OK, deviceAnomalyResponse.getStatusCode());
        Map<String, Object> deviceAnomalyResult = deviceAnomalyResponse.getBody();
        assertTrue((Boolean) deviceAnomalyResult.get("anomalyDetected"));
        assertTrue((Double) deviceAnomalyResult.get("anomalyScore") > 50.0);

        // Test credential leak exposure detection
        Map<String, Object> credentialLeakSignal = Map.of(
                "userId", testUser.getId().toString(),
                "email", testUser.getEmail(),
                "leakSource", "haveibeenpwned",
                "breachName", "Test Breach 2024",
                "breachDate", "2024-01-15",
                "severity", "high"
        );

        ResponseEntity<Map> credentialLeakResponse = restTemplate.postForEntity(
                baseUrl + "/auth/risk-signals/credential-leak",
                credentialLeakSignal,
                Map.class
        );

        assertEquals(HttpStatus.OK, credentialLeakResponse.getStatusCode());
        Map<String, Object> credentialLeakResult = credentialLeakResponse.getBody();
        assertTrue((Boolean) credentialLeakResult.get("leakDetected"));
        assertTrue((Boolean) credentialLeakResult.get("forcePasswordReset"));
        assertTrue((Boolean) credentialLeakResult.get("allSessionsInvalidated"));

        // Verify comprehensive audit events with risk factors
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        
        AuditEvent impossibleTravelEvent = auditEvents.stream()
                .filter(event -> event.getEventType().equals("security.impossible_travel.detected"))
                .findFirst()
                .orElse(null);
        assertNotNull(impossibleTravelEvent);
        assertTrue(Arrays.asList(impossibleTravelEvent.getRiskFactors()).contains("impossible_travel"));
        assertTrue(impossibleTravelEvent.getRiskScore() > 80.0);

        AuditEvent sessionInvalidationEvent = auditEvents.stream()
                .filter(event -> event.getEventType().equals("session.invalidated.risk"))
                .findFirst()
                .orElse(null);
        assertNotNull(sessionInvalidationEvent);
        assertEquals("high_risk_detected", sessionInvalidationEvent.getDescription());

        AuditEvent credentialLeakEvent = auditEvents.stream()
                .filter(event -> event.getEventType().equals("security.credential_leak.detected"))
                .findFirst()
                .orElse(null);
        assertNotNull(credentialLeakEvent);
        assertTrue(Arrays.asList(credentialLeakEvent.getRiskFactors()).contains("credential_leak"));
    }

    // Helper method to authenticate user and return access token
    private String authenticateUser(String email, String password) throws Exception {
        Map<String, Object> loginRequest = Map.of(
                "email", email,
                "password", password,
                "tenantCode", testTenant.getTenantCode()
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/auth/login/password",
                loginRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> result = response.getBody();
        return (String) result.get("accessToken");
    }
}
