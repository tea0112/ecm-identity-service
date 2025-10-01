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
import org.springframework.test.annotation.Commit;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                classes = {TestWebConfig.class})
@Testcontainers
class FR1AuthenticationSessionsIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("ecm_identity_fr1_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("ecm.audit.enabled", () -> "false");
        registry.add("ecm.multitenancy.enabled", () -> "false");
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
    @Commit
    void setUp() {
        baseUrl = "http://localhost:" + port;
        
        // Generate unique identifiers for this test run
        String uniqueId = String.valueOf(System.currentTimeMillis());
        String tenantCode = "test-tenant-" + uniqueId;
        String userEmail = "test-" + uniqueId + "@example.com";
        
        // Create test tenant
        testTenant = Tenant.builder()
                .tenantCode(tenantCode)
                .name("Test Tenant " + uniqueId)
                .domain("test-" + uniqueId + ".example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .settings("{}") // Valid JSON for jsonb column
                .build();
        testTenant = tenantRepository.save(testTenant);

        // Create test user
        testUser = User.builder()
                .email(userEmail)
                .firstName("Test")
                .lastName("User")
                .tenant(testTenant)
                .status(User.UserStatus.ACTIVE)
                .passwordHash("SecurePassword123!") // Using plain password since test uses NoOpPasswordEncoder
                .emailVerified(true)
                .mfaEnabled(false)
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
        Map<String, Object> loginResult = authenticateUserWithTokens(testUser.getEmail(), "SecurePassword123!");
        String accessToken = (String) loginResult.get("accessToken");
        String refreshToken = (String) loginResult.get("refreshToken");

        // Add a small delay to avoid rapid successive requests
        Thread.sleep(200);

        // Test token refresh
        Map<String, Object> refreshRequest = Map.of(
                "sessionId", accessToken, // Use the accessToken (which is the sessionId) from login
                "refreshToken", refreshToken // Use the actual refresh token from login
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

        // Add delay before testing refresh token rotation
        Thread.sleep(300);

        // Test refresh token rotation - use the old refresh token (should fail)
        Map<String, Object> oldRefreshRequest = Map.of(
                "sessionId", accessToken, // Use the original accessToken (sessionId)
                "refreshToken", refreshToken // Use the original refresh token
        );
        
        ResponseEntity<Map> oldRefreshResponse = restTemplate.postForEntity(
                baseUrl + "/auth/token/refresh",
                oldRefreshRequest,
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
                .credentialIdentifier("totp-" + testUser.getId()) // Required field
                .credentialData("JBSWY3DPEHPK3PXP") // Base32 encoded secret
                .status(UserCredential.CredentialStatus.ACTIVE)
                .metadata("{}") // Valid JSON for jsonb column
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

        // Test MFA verification - first authenticate to get a session
        String accessToken = authenticateUser(testUser.getEmail(), "SecurePassword123!");
        
        Map<String, Object> mfaVerifyRequest = Map.of(
                "sessionId", accessToken, // Use accessToken as sessionId
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
        // accessToken already obtained from MFA verification above
        
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
                .deviceMetadata("{}") // Valid JSON for jsonb column
                .build();
        testDevice = deviceRepository.save(testDevice);
        deviceRepository.flush(); // Ensure the device is persisted before creating the session

        String accessToken = authenticateUser(testUser.getEmail(), "SecurePassword123!");
        
        // Get the session created by login and associate it with the device
        Optional<UserSession> sessionOpt = sessionRepository.findBySessionId(accessToken);
        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            session.setDevice(testDevice);
            sessionRepository.save(session);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // Test session listing
        ResponseEntity<Map> sessionsResponse = restTemplate.exchange(
                baseUrl + "/auth/sessions",
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

        // Test device binding verification (before revoking the session)
        Map<String, Object> deviceBindingRequest = Map.of(
                "sessionId", accessToken,
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

        // Test session revocation (after device binding verification)
        String sessionIdToRevoke = (String) session.get("sessionId");
        ResponseEntity<Map> revokeResponse = restTemplate.exchange(
                baseUrl + "/auth/user/sessions/" + sessionIdToRevoke + "/revoke",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, revokeResponse.getStatusCode());
        Map<String, Object> revokeResult = revokeResponse.getBody();
        assertTrue((Boolean) revokeResult.get("revoked"));

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("session.terminated")));
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
                "email", socialUser.getEmail(),
                "linkType", "social"
        );

        ResponseEntity<Map> linkingResponse = restTemplate.exchange(
                baseUrl + "/user/accounts/link?sessionId=" + accessToken,
                HttpMethod.POST,
                new HttpEntity<>(linkingRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, linkingResponse.getStatusCode());
        Map<String, Object> linkingResult = linkingResponse.getBody();
        assertTrue((Boolean) linkingResult.get("success"));
        assertEquals("Accounts linked successfully", linkingResult.get("message"));
        assertNotNull(linkingResult.get("linkedAccountId"));
        assertEquals(socialUser.getEmail(), linkingResult.get("linkedAccountEmail"));

        // Test account merge with full audit trail
        Map<String, Object> mergeRequest = Map.of(
                "email", socialUser.getEmail(),
                "mergeStrategy", "preserve_primary"
        );

        ResponseEntity<Map> mergeResponse = restTemplate.exchange(
                baseUrl + "/user/accounts/merge?sessionId=" + accessToken,
                HttpMethod.POST,
                new HttpEntity<>(mergeRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, mergeResponse.getStatusCode());
        Map<String, Object> mergeResult = mergeResponse.getBody();
        assertTrue((Boolean) mergeResult.get("success"));
        assertEquals("Accounts merged successfully", mergeResult.get("message"));
        assertNotNull(mergeResult.get("mergedAccountId"));
        assertEquals(socialUser.getEmail(), mergeResult.get("mergedAccountEmail"));
        assertEquals(testUser.getId().toString(), mergeResult.get("primaryAccountId"));

        // Test merge reversal capability
        Map<String, Object> reversalRequest = Map.of(
                "mergeId", "test-merge-id-123",
                "reason", "User requested account separation"
        );

        ResponseEntity<Map> reversalResponse = restTemplate.exchange(
                baseUrl + "/user/accounts/merge/reverse?sessionId=" + accessToken,
                HttpMethod.POST,
                new HttpEntity<>(reversalRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, reversalResponse.getStatusCode());
        Map<String, Object> reversalResult = reversalResponse.getBody();
        assertTrue((Boolean) reversalResult.get("success"));
        assertEquals("Account merge reversed successfully", reversalResult.get("message"));
        assertNotNull(reversalResult.get("reversedMergeId"));
        assertNotNull(reversalResult.get("restoredAccountId"));

        // Verify comprehensive audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("ACCOUNTS_LINKED")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("ACCOUNTS_MERGED")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("ACCOUNT_MERGE_REVERSED")));
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
                .status(User.UserStatus.ACTIVE)
                .passwordHash("SecurePassword123!")
                .emailVerified(true)
                .mfaEnabled(false)
                .build();
        minorUser = userRepository.save(minorUser);

        // Authenticate to get a session
        String accessToken = authenticateUser(minorUser.getEmail(), "SecurePassword123!");

        // Test age verification
        Map<String, Object> ageVerificationRequest = Map.of(
                "sessionId", accessToken,
                "age", 12
        );

        ResponseEntity<Map> ageVerificationResponse = restTemplate.postForEntity(
                baseUrl + "/auth/age-verification",
                ageVerificationRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, ageVerificationResponse.getStatusCode());
        Map<String, Object> ageVerificationResult = ageVerificationResponse.getBody();
        assertTrue((Boolean) ageVerificationResult.get("success"));
        assertFalse((Boolean) ageVerificationResult.get("ageVerified"));
        assertEquals(12, ageVerificationResult.get("age"));
        assertTrue((Boolean) ageVerificationResult.get("requiresParentalConsent"));

        // Test parental consent initiation
        Map<String, Object> parentalConsentRequest = Map.of(
                "sessionId", accessToken,
                "parentEmail", "parent@example.com"
        );

        ResponseEntity<Map> consentResponse = restTemplate.postForEntity(
                baseUrl + "/auth/parental-consent/initiate",
                parentalConsentRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, consentResponse.getStatusCode());
        Map<String, Object> consentResult = consentResponse.getBody();
        assertTrue((Boolean) consentResult.get("success"));
        assertNotNull(consentResult.get("consentToken"));
        assertNotNull(consentResult.get("consentUrl"));

        // Test parental consent verification
        Map<String, Object> consentVerificationRequest = Map.of(
                "consentToken", consentResult.get("consentToken")
        );

        ResponseEntity<Map> consentVerificationResponse = restTemplate.postForEntity(
                baseUrl + "/auth/parental-consent/verify",
                consentVerificationRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, consentVerificationResponse.getStatusCode());
        Map<String, Object> consentVerificationResult = consentVerificationResponse.getBody();
        assertTrue((Boolean) consentVerificationResult.get("success"));
        assertTrue((Boolean) consentVerificationResult.get("consentVerified"));
        assertNotNull(consentVerificationResult.get("consentDate"));

        // Verify audit events include parental consent tracking
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(minorUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("AGE_VERIFICATION")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("PARENTAL_CONSENT_INITIATED")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("PARENTAL_CONSENT_VERIFIED")));
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
        deviceRepository.flush(); // Ensure the device is persisted before creating the session

        UserSession activeSession = UserSession.builder()
                .user(testUser)
                .sessionId("session-456")
                .status(UserSession.SessionStatus.ACTIVE)
                .ipAddress("192.168.1.100")
                .expiresAt(Instant.now().plus(2, ChronoUnit.HOURS))
                .lastActivityAt(Instant.now())
                .riskScore(25.0)
                .refreshTokenHash("refresh-token-hash-456")
                .authenticationMethod(UserSession.AuthenticationMethod.PASSWORD)
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
        Map<String, Object> loginResult = authenticateUserWithTokens(email, password);
        return (String) loginResult.get("accessToken");
    }

    private Map<String, Object> authenticateUserWithTokens(String email, String password) throws Exception {
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
        return response.getBody();
    }
}
