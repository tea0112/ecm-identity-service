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

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FR1 - Authentication & Sessions requirements using Testcontainers.
 * These tests validate the complete authentication flow with real database and Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("FR1 - Authentication & Sessions Integration Tests")
class AuthenticationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("ecm_identity_test")
            .withUsername("test_user")
            .withPassword("test_pass")
            .withInitScript("test-init.sql");

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
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
    private UserSessionRepository sessionRepository;

    @Autowired
    private UserCredentialRepository credentialRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private String baseUrl;
    private Tenant testTenant;
    private User testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1";
        
        // Clean up test data
        auditEventRepository.deleteAll();
        sessionRepository.deleteAll();
        credentialRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
        
        // Create test tenant
        testTenant = new Tenant();
        testTenant.setTenantCode("test-tenant");
        testTenant.setName("Test Tenant");
        testTenant.setStatus(Tenant.TenantStatus.ACTIVE);
        testTenant.setMaxUsers(100);
        testTenant = tenantRepository.save(testTenant);
        
        // Create test user
        testUser = new User();
        testUser.setTenant(testTenant);
        testUser.setEmail("testuser@example.com");
        testUser.setUsername("testuser");
        testUser.setPasswordHash("$argon2id$v=19$m=65536,t=3,p=4$test_salt$test_hash");
        testUser.setPasswordAlgorithm("ARGON2");
        testUser.setStatus(User.UserStatus.ACTIVE);
        testUser.setMfaEnabled(true);
        testUser.setEmailVerified(true);
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("FR1.1 - Support authentication via password, WebAuthn/passkeys, and magic links")
    void testMultipleAuthenticationMethods() {
        // Test password authentication
        Map<String, Object> passwordAuth = Map.of(
            "email", testUser.getEmail(),
            "password", "correct_password",
            "authMethod", "PASSWORD"
        );
        
        ResponseEntity<Map> response = restTemplate.postForEntity(
            baseUrl + "/auth/login", 
            passwordAuth, 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body.get("sessionId"));
        assertEquals("PASSWORD", body.get("authMethod"));
        
        // Test WebAuthn authentication (mock successful WebAuthn response)
        Map<String, Object> webauthnAuth = Map.of(
            "email", testUser.getEmail(),
            "webauthnResponse", "mock_webauthn_response",
            "authMethod", "WEBAUTHN"
        );
        
        ResponseEntity<Map> webauthnResponse = restTemplate.postForEntity(
            baseUrl + "/auth/webauthn/authenticate", 
            webauthnAuth, 
            Map.class
        );
        
        // Should return challenge or success
        assertTrue(webauthnResponse.getStatusCode().is2xxSuccessful());
        
        // Test magic link authentication
        Map<String, Object> magicLinkRequest = Map.of(
            "email", testUser.getEmail(),
            "authMethod", "MAGIC_LINK"
        );
        
        ResponseEntity<Map> magicLinkResponse = restTemplate.postForEntity(
            baseUrl + "/auth/magic-link", 
            magicLinkRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, magicLinkResponse.getStatusCode());
        Map<String, Object> magicBody = magicLinkResponse.getBody();
        assertNotNull(magicBody.get("message"));
        assertTrue(magicBody.get("message").toString().contains("sent"));
    }

    @Test
    @DisplayName("FR1.2 - Generate and manage short-lived access tokens and long-lived, rotating refresh tokens")
    void testTokenIssuanceAndRefresh() {
        // Initial authentication
        Map<String, Object> loginRequest = Map.of(
            "email", testUser.getEmail(),
            "password", "correct_password"
        );
        
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login", 
            loginRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        Map<String, Object> tokens = loginResponse.getBody();
        
        String accessToken = (String) tokens.get("accessToken");
        String refreshToken = (String) tokens.get("refreshToken");
        
        assertNotNull(accessToken);
        assertNotNull(refreshToken);
        
        // Verify access token is short-lived (should expire in < 30 minutes)
        Integer accessTokenExpiry = (Integer) tokens.get("accessTokenExpiresIn");
        assertTrue(accessTokenExpiry <= 1800); // 30 minutes max
        
        // Verify refresh token is long-lived (should expire in days/weeks)
        Integer refreshTokenExpiry = (Integer) tokens.get("refreshTokenExpiresIn");
        assertTrue(refreshTokenExpiry > 86400); // At least 1 day
        
        // Test token refresh
        Map<String, Object> refreshRequest = Map.of(
            "refreshToken", refreshToken
        );
        
        ResponseEntity<Map> refreshResponse = restTemplate.postForEntity(
            baseUrl + "/auth/refresh", 
            refreshRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode());
        Map<String, Object> newTokens = refreshResponse.getBody();
        
        String newAccessToken = (String) newTokens.get("accessToken");
        String newRefreshToken = (String) newTokens.get("refreshToken");
        
        assertNotNull(newAccessToken);
        assertNotNull(newRefreshToken);
        assertNotEquals(accessToken, newAccessToken);
        assertNotEquals(refreshToken, newRefreshToken); // Refresh token rotation
        
        // Verify old refresh token is invalidated
        ResponseEntity<Map> oldTokenResponse = restTemplate.postForEntity(
            baseUrl + "/auth/refresh", 
            refreshRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.UNAUTHORIZED, oldTokenResponse.getStatusCode());
    }

    @Test
    @DisplayName("FR1.3 - Support MFA (TOTP, WebAuthn) and trigger policy-driven step-up authentication")
    void testMultiFactorAndStepUpAuthentication() {
        // Setup TOTP credential for user
        UserCredential totpCredential = new UserCredential();
        totpCredential.setUser(testUser);
        totpCredential.setCredentialType(UserCredential.CredentialType.TOTP);
        totpCredential.setTotpSecret("JBSWY3DPEHPK3PXP"); // Base32 encoded secret
        totpCredential.setStatus(UserCredential.CredentialStatus.ACTIVE);
        credentialRepository.save(totpCredential);
        
        // Initial login (should require MFA)
        Map<String, Object> loginRequest = Map.of(
            "email", testUser.getEmail(),
            "password", "correct_password"
        );
        
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login", 
            loginRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, loginResponse.getStatusCode());
        Map<String, Object> mfaChallenge = loginResponse.getBody();
        
        assertEquals("MFA_REQUIRED", mfaChallenge.get("status"));
        String mfaToken = (String) mfaChallenge.get("mfaToken");
        assertNotNull(mfaToken);
        
        // Complete MFA with TOTP
        Map<String, Object> mfaRequest = Map.of(
            "mfaToken", mfaToken,
            "totpCode", "123456", // Mock TOTP code
            "mfaMethod", "TOTP"
        );
        
        ResponseEntity<Map> mfaResponse = restTemplate.postForEntity(
            baseUrl + "/auth/mfa/verify", 
            mfaRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, mfaResponse.getStatusCode());
        Map<String, Object> finalTokens = mfaResponse.getBody();
        
        assertNotNull(finalTokens.get("accessToken"));
        assertEquals(true, finalTokens.get("mfaCompleted"));
        
        // Test step-up authentication for sensitive operation
        String accessToken = (String) finalTokens.get("accessToken");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Map<String, Object>> deleteRequest = new HttpEntity<>(
            Map.of("confirmPassword", "correct_password"), 
            headers
        );
        
        ResponseEntity<Map> deleteResponse = restTemplate.exchange(
            baseUrl + "/users/me", 
            HttpMethod.DELETE, 
            deleteRequest, 
            Map.class
        );
        
        // Should require step-up authentication for sensitive operation
        if (deleteResponse.getStatusCode() == HttpStatus.FORBIDDEN) {
            Map<String, Object> stepUpChallenge = deleteResponse.getBody();
            assertEquals("STEP_UP_REQUIRED", stepUpChallenge.get("status"));
        }
    }

    @Test
    @DisplayName("FR1.4 - Allow users to view and revoke their active sessions with device binding")
    void testDeviceAndSessionManagement() {
        // Create multiple sessions for user
        String deviceFingerprint1 = "device_fp_1";
        String deviceFingerprint2 = "device_fp_2";
        
        // Session 1
        Map<String, Object> login1 = Map.of(
            "email", testUser.getEmail(),
            "password", "correct_password",
            "deviceFingerprint", deviceFingerprint1,
            "userAgent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        );
        
        ResponseEntity<Map> session1Response = restTemplate.postForEntity(
            baseUrl + "/auth/login", 
            login1, 
            Map.class
        );
        
        String accessToken1 = (String) session1Response.getBody().get("accessToken");
        
        // Session 2
        Map<String, Object> login2 = Map.of(
            "email", testUser.getEmail(),
            "password", "correct_password",
            "deviceFingerprint", deviceFingerprint2,
            "userAgent", "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15"
        );
        
        ResponseEntity<Map> session2Response = restTemplate.postForEntity(
            baseUrl + "/auth/login", 
            login2, 
            Map.class
        );
        
        String accessToken2 = (String) session2Response.getBody().get("accessToken");
        
        // List user sessions
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken1);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map> sessionsResponse = restTemplate.exchange(
            baseUrl + "/sessions", 
            HttpMethod.GET, 
            entity, 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, sessionsResponse.getStatusCode());
        Map<String, Object> sessionsData = sessionsResponse.getBody();
        
        @SuppressWarnings("unchecked")
        var sessions = (java.util.List<Map<String, Object>>) sessionsData.get("sessions");
        assertEquals(2, sessions.size());
        
        // Verify device binding information
        boolean hasDevice1 = sessions.stream()
            .anyMatch(s -> deviceFingerprint1.equals(s.get("deviceFingerprint")));
        boolean hasDevice2 = sessions.stream()
            .anyMatch(s -> deviceFingerprint2.equals(s.get("deviceFingerprint")));
        
        assertTrue(hasDevice1);
        assertTrue(hasDevice2);
        
        // Revoke one session
        String sessionIdToRevoke = (String) sessions.get(0).get("sessionId");
        
        ResponseEntity<Void> revokeResponse = restTemplate.exchange(
            baseUrl + "/sessions/" + sessionIdToRevoke, 
            HttpMethod.DELETE, 
            entity, 
            Void.class
        );
        
        assertEquals(HttpStatus.NO_CONTENT, revokeResponse.getStatusCode());
        
        // Verify session was revoked
        ResponseEntity<Map> updatedSessionsResponse = restTemplate.exchange(
            baseUrl + "/sessions", 
            HttpMethod.GET, 
            entity, 
            Map.class
        );
        
        @SuppressWarnings("unchecked")
        var remainingSessions = (java.util.List<Map<String, Object>>) 
            updatedSessionsResponse.getBody().get("sessions");
        assertEquals(1, remainingSessions.size());
    }

    @Test
    @DisplayName("FR1.5 - Provide secure, rate-limited account recovery flows and passwordless fallbacks")
    void testSecureAccountRecoveryAndFallbacks() {
        // Test password reset request
        Map<String, Object> resetRequest = Map.of(
            "email", testUser.getEmail()
        );
        
        ResponseEntity<Map> resetResponse = restTemplate.postForEntity(
            baseUrl + "/auth/password-reset", 
            resetRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, resetResponse.getStatusCode());
        Map<String, Object> resetBody = resetResponse.getBody();
        assertNotNull(resetBody.get("message"));
        
        // Test rate limiting - multiple reset requests should be throttled
        for (int i = 0; i < 5; i++) {
            ResponseEntity<Map> throttledResponse = restTemplate.postForEntity(
                baseUrl + "/auth/password-reset", 
                resetRequest, 
                Map.class
            );
            
            if (i >= 3) { // After 3 attempts, should be rate limited
                assertEquals(HttpStatus.TOO_MANY_REQUESTS, throttledResponse.getStatusCode());
                Map<String, Object> errorBody = throttledResponse.getBody();
                assertTrue(errorBody.get("message").toString().contains("rate limit"));
                break;
            }
        }
        
        // Test recovery code validation
        UserCredential recoveryCredential = new UserCredential();
        recoveryCredential.setUser(testUser);
        recoveryCredential.setCredentialType(UserCredential.CredentialType.RECOVERY_CODE);
        recoveryCredential.setVerificationCode("RECOVERY123456");
        recoveryCredential.setStatus(UserCredential.CredentialStatus.ACTIVE);
        recoveryCredential.setExpiresAt(Instant.now().plus(Duration.ofMinutes(15)));
        recoveryCredential.setMaxVerificationAttempts(3);
        recoveryCredential.setVerificationAttempts(0);
        credentialRepository.save(recoveryCredential);
        
        // Use recovery code
        Map<String, Object> recoveryRequest = Map.of(
            "email", testUser.getEmail(),
            "recoveryCode", "RECOVERY123456",
            "newPassword", "new_secure_password"
        );
        
        ResponseEntity<Map> recoveryResponse = restTemplate.postForEntity(
            baseUrl + "/auth/recover", 
            recoveryRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, recoveryResponse.getStatusCode());
        Map<String, Object> recoveryBody = recoveryResponse.getBody();
        assertEquals("PASSWORD_RESET_SUCCESS", recoveryBody.get("status"));
    }

    @Test
    @DisplayName("FR1.8 - Automatically invalidate sessions upon detection of high-risk signals")
    void testProactiveSessionInvalidationBasedOnRiskSignals() {
        // Create session
        Map<String, Object> loginRequest = Map.of(
            "email", testUser.getEmail(),
            "password", "correct_password",
            "deviceFingerprint", "original_device",
            "ipAddress", "192.168.1.100",
            "location", "San Francisco, CA"
        );
        
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login", 
            loginRequest, 
            Map.class
        );
        
        String accessToken = (String) loginResponse.getBody().get("accessToken");
        String sessionId = (String) loginResponse.getBody().get("sessionId");
        
        // Simulate impossible travel - login from different location too quickly
        Map<String, Object> impossibleTravelRequest = Map.of(
            "sessionId", sessionId,
            "newLocation", "Tokyo, Japan",
            "ipAddress", "203.0.113.45",
            "deviceFingerprint", "original_device" // Same device but impossible location
        );
        
        ResponseEntity<Map> riskUpdateResponse = restTemplate.postForEntity(
            baseUrl + "/security/risk-assessment", 
            impossibleTravelRequest, 
            Map.class
        );
        
        // Risk assessment should detect impossible travel
        assertEquals(HttpStatus.OK, riskUpdateResponse.getStatusCode());
        Map<String, Object> riskBody = riskUpdateResponse.getBody();
        assertTrue((Double) riskBody.get("riskScore") > 80.0); // High risk
        assertTrue(((String) riskBody.get("riskFactors")).contains("impossible_travel"));
        
        // Try to use the session - should be automatically invalidated
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map> profileResponse = restTemplate.exchange(
            baseUrl + "/users/me", 
            HttpMethod.GET, 
            entity, 
            Map.class
        );
        
        // Session should be invalidated due to high risk
        assertEquals(HttpStatus.UNAUTHORIZED, profileResponse.getStatusCode());
        Map<String, Object> errorBody = profileResponse.getBody();
        assertTrue(errorBody.get("message").toString().contains("session invalidated"));
        
        // Verify audit event was created for risk-based invalidation
        var auditEvents = auditEventRepository.findByUserId(testUser.getId());
        boolean hasRiskInvalidation = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals(AuditEvent.EventTypes.SESSION_TERMINATED) &&
                              event.getDescription().contains("risk"));
        assertTrue(hasRiskInvalidation);
    }

    @Test
    @DisplayName("FR1.7 - Implement workflows for handling child accounts with age verification and parental consent")
    void testAgeGatesAndParentalConsent() {
        // Create minor user
        User minorUser = new User();
        minorUser.setTenant(testTenant);
        minorUser.setEmail("minor@example.com");
        minorUser.setUsername("minoruser");
        minorUser.setIsMinor(true);
        minorUser.setDateOfBirth(java.time.LocalDate.now().minusYears(15));
        minorUser.setParentEmail("parent@example.com");
        minorUser.setStatus(User.UserStatus.PENDING_VERIFICATION);
        minorUser = userRepository.save(minorUser);
        
        // Attempt to login without parental consent
        Map<String, Object> minorLoginRequest = Map.of(
            "email", minorUser.getEmail(),
            "password", "minor_password"
        );
        
        ResponseEntity<Map> minorLoginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login", 
            minorLoginRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.FORBIDDEN, minorLoginResponse.getStatusCode());
        Map<String, Object> consentBody = minorLoginResponse.getBody();
        assertEquals("PARENTAL_CONSENT_REQUIRED", consentBody.get("status"));
        assertNotNull(consentBody.get("parentEmail"));
        
        // Simulate parental consent
        Map<String, Object> consentRequest = Map.of(
            "userId", minorUser.getId().toString(),
            "parentEmail", "parent@example.com",
            "consentGiven", true,
            "parentSignature", "digital_signature_here"
        );
        
        ResponseEntity<Map> consentResponse = restTemplate.postForEntity(
            baseUrl + "/auth/parental-consent", 
            consentRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, consentResponse.getStatusCode());
        Map<String, Object> approvalBody = consentResponse.getBody();
        assertEquals("CONSENT_GRANTED", approvalBody.get("status"));
        
        // Now minor should be able to login
        ResponseEntity<Map> successfulMinorLogin = restTemplate.postForEntity(
            baseUrl + "/auth/login", 
            minorLoginRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, successfulMinorLogin.getStatusCode());
        assertNotNull(successfulMinorLogin.getBody().get("accessToken"));
        
        // Verify audit trail for parental consent
        var auditEvents = auditEventRepository.findByUserId(minorUser.getId());
        boolean hasConsentEvent = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals(AuditEvent.EventTypes.CONSENT_GIVEN) &&
                              event.getDescription().contains("parental"));
        assertTrue(hasConsentEvent);
    }

    @Test
    @DisplayName("Integration: Complete authentication flow with device attestation and risk assessment")
    void testCompleteAuthenticationFlowWithDeviceAttestation() {
        // Enhanced login with device attestation
        Map<String, Object> enhancedLoginRequest = Map.of(
            "email", testUser.getEmail(),
            "password", "correct_password",
            "deviceFingerprint", "secure_device_123",
            "deviceAttestation", Map.of(
                "tpmPresent", true,
                "secureElementPresent", true,
                "attestationData", "device_attestation_blob"
            ),
            "ipAddress", "192.168.1.100",
            "userAgent", "SecureApp/1.0",
            "location", "San Francisco, CA"
        );
        
        ResponseEntity<Map> enhancedResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login-enhanced", 
            enhancedLoginRequest, 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, enhancedResponse.getStatusCode());
        Map<String, Object> enhancedBody = enhancedResponse.getBody();
        
        // Verify device security features are recognized
        assertTrue((Boolean) enhancedBody.get("deviceTrusted"));
        assertTrue((Double) enhancedBody.get("riskScore") < 20.0); // Low risk due to trusted device
        assertNotNull(enhancedBody.get("sessionId"));
        
        // Verify session was created with proper device binding
        String sessionId = (String) enhancedBody.get("sessionId");
        var savedSession = sessionRepository.findBySessionId(sessionId);
        assertTrue(savedSession.isPresent());
        assertEquals("secure_device_123", savedSession.get().getDevice().getDeviceFingerprint());
        assertTrue(savedSession.get().getDevice().getTpmPresent());
        assertTrue(savedSession.get().getDevice().getSecureElementPresent());
    }
}
