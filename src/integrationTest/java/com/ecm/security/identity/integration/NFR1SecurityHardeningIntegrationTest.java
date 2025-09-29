package com.ecm.security.identity.integration;

import com.ecm.security.identity.domain.*;
import com.ecm.security.identity.repository.*;
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
 * Comprehensive integration tests for NFR1 - Security Hardening requirements.
 * Tests threat mitigation, token security, rate limiting, secrets management,
 * backup safety, key rotation, and cryptographic agility.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Transactional
class NFR1SecurityHardeningIntegrationTest {

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
    private AuditEventRepository auditEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private Tenant testTenant;
    private User testUser;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        
        testTenant = Tenant.builder()
                .tenantCode("test-tenant")
                .name("Test Tenant")
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        testTenant = tenantRepository.save(testTenant);

        testUser = User.builder()
                .email("testuser@example.com")
                .firstName("Test")
                .lastName("User")
                .tenant(testTenant)
                .status(User.UserStatus.ACTIVE)
                .build();
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("NFR1.1 - Threat Mitigation: Credential Stuffing, MFA Fatigue, Replay Attacks")
    void testThreatMitigationDefenses() throws Exception {
        // Test credential stuffing protection
        String attackerIP = "203.0.113.100";
        List<String> commonPasswords = Arrays.asList(
                "password123", "admin", "123456", "password", "qwerty"
        );

        // Attempt credential stuffing attack
        for (int i = 0; i < commonPasswords.size(); i++) {
            Map<String, Object> loginAttempt = Map.of(
                    "email", testUser.getEmail(),
                    "password", commonPasswords.get(i),
                    "tenantCode", testTenant.getTenantCode(),
                    "clientIP", attackerIP,
                    "userAgent", "AttackerBot/1.0"
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/auth/login/password",
                    loginAttempt,
                    Map.class
            );

            if (i < 3) {
                assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            } else {
                // After 3 attempts, should be rate limited
                assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
                Map<String, Object> result = response.getBody();
                assertEquals("credential_stuffing_detected", result.get("error"));
                assertTrue((Boolean) result.get("ipBlocked"));
            }
        }

        // Test MFA fatigue protection
        String validToken = authenticateUser(testUser.getEmail(), "correct-password");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(validToken);

        // Simulate rapid MFA requests (MFA fatigue attack)
        for (int i = 0; i < 10; i++) {
            Map<String, Object> mfaRequest = Map.of(
                    "userId", testUser.getId().toString(),
                    "mfaType", "totp",
                    "requestSource", "suspicious_automation"
            );

            ResponseEntity<Map> mfaResponse = restTemplate.exchange(
                    baseUrl + "/auth/mfa/request",
                    HttpMethod.POST,
                    new HttpEntity<>(mfaRequest, headers),
                    Map.class
            );

            if (i < 5) {
                assertEquals(HttpStatus.OK, mfaResponse.getStatusCode());
            } else {
                assertEquals(HttpStatus.TOO_MANY_REQUESTS, mfaResponse.getStatusCode());
                Map<String, Object> result = mfaResponse.getBody();
                assertEquals("mfa_fatigue_protection", result.get("error"));
                assertTrue((Integer) result.get("cooldownSeconds") > 0);
            }
        }

        // Test replay attack protection with single-use tokens
        Map<String, Object> magicLinkRequest = Map.of(
                "email", testUser.getEmail(),
                "tenantCode", testTenant.getTenantCode()
        );

        ResponseEntity<Map> magicLinkResponse = restTemplate.postForEntity(
                baseUrl + "/auth/magic-link/request",
                magicLinkRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, magicLinkResponse.getStatusCode());
        String magicToken = (String) magicLinkResponse.getBody().get("token");

        // First use should succeed
        Map<String, Object> firstUse = Map.of(
                "token", magicToken,
                "tenantCode", testTenant.getTenantCode()
        );

        ResponseEntity<Map> firstUseResponse = restTemplate.postForEntity(
                baseUrl + "/auth/magic-link/verify",
                firstUse,
                Map.class
        );

        assertEquals(HttpStatus.OK, firstUseResponse.getStatusCode());

        // Second use (replay) should fail
        ResponseEntity<Map> replayResponse = restTemplate.postForEntity(
                baseUrl + "/auth/magic-link/verify",
                firstUse,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, replayResponse.getStatusCode());
        Map<String, Object> replayResult = replayResponse.getBody();
        assertEquals("token_already_used", replayResult.get("error"));

        // Verify comprehensive audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("security.credential_stuffing.detected")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("security.mfa_fatigue.blocked")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("security.replay_attack.prevented")));
    }

    @Test
    @DisplayName("NFR1.2 - Advanced Token & Session Hygiene")
    void testAdvancedTokenAndSessionHygiene() throws Exception {
        // Test JWT security with rotated keys
        Map<String, Object> tokenRequest = Map.of(
                "clientId", "test-client",
                "clientSecret", "test-secret",
                "grantType", "client_credentials",
                "scope", "api:read"
        );

        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                baseUrl + "/oauth2/token",
                tokenRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, tokenResponse.getStatusCode());
        Map<String, Object> tokenResult = tokenResponse.getBody();
        String accessToken = (String) tokenResult.get("access_token");
        String refreshToken = (String) tokenResult.get("refresh_token");
        assertNotNull(accessToken);
        assertNotNull(refreshToken);

        // Verify JWT has proper security claims
        ResponseEntity<Map> tokenInfoResponse = restTemplate.exchange(
                baseUrl + "/oauth2/token/info",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("token", accessToken)),
                Map.class
        );

        assertEquals(HttpStatus.OK, tokenInfoResponse.getStatusCode());
        Map<String, Object> tokenInfo = tokenInfoResponse.getBody();
        assertNotNull(tokenInfo.get("kid")); // Key ID present
        assertNotNull(tokenInfo.get("aud")); // Audience claim
        assertNotNull(tokenInfo.get("iss")); // Issuer claim
        assertTrue((Long) tokenInfo.get("exp") > System.currentTimeMillis() / 1000); // Not expired

        // Test rotating refresh tokens with family revocation
        Map<String, Object> refreshRequest = Map.of(
                "grant_type", "refresh_token",
                "refresh_token", refreshToken
        );

        ResponseEntity<Map> refreshResponse = restTemplate.postForEntity(
                baseUrl + "/oauth2/token",
                refreshRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode());
        Map<String, Object> refreshResult = refreshResponse.getBody();
        String newAccessToken = (String) refreshResult.get("access_token");
        String newRefreshToken = (String) refreshResult.get("refresh_token");
        
        assertNotEquals(accessToken, newAccessToken);
        assertNotEquals(refreshToken, newRefreshToken);

        // Verify old refresh token is invalidated
        ResponseEntity<Map> oldRefreshResponse = restTemplate.postForEntity(
                baseUrl + "/oauth2/token",
                refreshRequest,
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, oldRefreshResponse.getStatusCode());
        Map<String, Object> oldRefreshResult = oldRefreshResponse.getBody();
        assertEquals("invalid_grant", oldRefreshResult.get("error"));

        // Test secure cookie policies
        ResponseEntity<String> cookieResponse = restTemplate.exchange(
                baseUrl + "/auth/session/cookie",
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class
        );

        assertEquals(HttpStatus.OK, cookieResponse.getStatusCode());
        List<String> setCookieHeaders = cookieResponse.getHeaders().get("Set-Cookie");
        assertNotNull(setCookieHeaders);
        
        boolean hasSecureCookie = setCookieHeaders.stream().anyMatch(cookie ->
                cookie.contains("Secure") && cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict")
        );
        assertTrue(hasSecureCookie);

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("token.refresh.rotated")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("token.family.revoked")));
    }

    @Test
    @DisplayName("NFR1.3 - Rate Limiting & Lockout Protection")
    void testRateLimitingAndLockoutProtection() throws Exception {
        // Test intelligent throttling
        String legitimateIP = "192.168.1.100";
        String attackerIP = "203.0.113.200";

        // Legitimate user should have higher rate limits
        for (int i = 0; i < 10; i++) {
            Map<String, Object> legitimateRequest = Map.of(
                    "email", testUser.getEmail(),
                    "password", "wrong-password",
                    "clientIP", legitimateIP,
                    "userAgent", "Mozilla/5.0 (legitimate browser)"
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/auth/login/password",
                    legitimateRequest,
                    Map.class
            );

            if (i < 8) {
                assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            } else {
                assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            }
        }

        // Attacker should be limited more aggressively
        for (int i = 0; i < 5; i++) {
            Map<String, Object> attackerRequest = Map.of(
                    "email", testUser.getEmail(),
                    "password", "wrong-password",
                    "clientIP", attackerIP,
                    "userAgent", "AttackerBot/1.0",
                    "suspiciousPatterns", Arrays.asList("automated", "rapid_fire")
            );

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/auth/login/password",
                    attackerRequest,
                    Map.class
            );

            if (i < 3) {
                assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            } else {
                assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
                Map<String, Object> result = response.getBody();
                assertEquals("aggressive_rate_limiting", result.get("reason"));
            }
        }

        // Test account lockout protection against malicious lockouts
        String maliciousIP = "203.0.113.300";
        
        Map<String, Object> maliciousLockoutRequest = Map.of(
                "email", testUser.getEmail(),
                "password", "intentionally-wrong",
                "clientIP", maliciousIP,
                "userAgent", "MaliciousBot/1.0",
                "intent", "lockout_attack"
        );

        // Attempt to cause malicious lockout
        for (int i = 0; i < 20; i++) {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/auth/login/password",
                    maliciousLockoutRequest,
                    Map.class
            );

            if (i >= 3) {
                assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
                Map<String, Object> result = response.getBody();
                assertTrue(result.containsKey("maliciousLockoutPrevented"));
            }
        }

        // Verify legitimate user can still login from different IP
        Map<String, Object> legitimateLoginRequest = Map.of(
                "email", testUser.getEmail(),
                "password", "correct-password",
                "clientIP", "192.168.1.101", // Different legitimate IP
                "userAgent", "Mozilla/5.0 (legitimate browser)"
        );

        ResponseEntity<Map> legitimateLoginResponse = restTemplate.postForEntity(
                baseUrl + "/auth/login/password",
                legitimateLoginRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, legitimateLoginResponse.getStatusCode());

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("security.rate_limiting.intelligent")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("security.malicious_lockout.prevented")));
    }

    @Test
    @DisplayName("NFR1.4 - Secrets & Supply-Chain Security")
    void testSecretsAndSupplyChainSecurity() throws Exception {
        HttpHeaders adminHeaders = createAdminHeaders();

        // Test central vault integration
        Map<String, Object> secretRequest = Map.of(
                "secretType", "database_password",
                "secretName", "postgres-main-db",
                "tenantCode", testTenant.getTenantCode(),
                "rotationPolicy", Map.of(
                        "enabled", true,
                        "intervalDays", 30
                )
        );

        ResponseEntity<Map> secretResponse = restTemplate.exchange(
                baseUrl + "/admin/secrets/store",
                HttpMethod.POST,
                new HttpEntity<>(secretRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, secretResponse.getStatusCode());
        Map<String, Object> secretResult = secretResponse.getBody();
        assertNotNull(secretResult.get("secretId"));
        assertTrue((Boolean) secretResult.get("storedInVault"));
        assertNotNull(secretResult.get("vaultPath"));

        // Test secret retrieval (should be audited)
        String secretId = (String) secretResult.get("secretId");
        ResponseEntity<Map> retrievalResponse = restTemplate.exchange(
                baseUrl + "/admin/secrets/" + secretId + "/retrieve",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, retrievalResponse.getStatusCode());
        Map<String, Object> retrievalResult = retrievalResponse.getBody();
        assertTrue((Boolean) retrievalResult.get("accessGranted"));
        assertNotNull(retrievalResult.get("accessToken"));
        
        // Secret value should not be in response for security
        assertNull(retrievalResult.get("secretValue"));

        // Test SBOM (Software Bill of Materials) validation
        ResponseEntity<Map> sbomResponse = restTemplate.exchange(
                baseUrl + "/admin/security/sbom/validate",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, sbomResponse.getStatusCode());
        Map<String, Object> sbomResult = sbomResponse.getBody();
        assertTrue((Boolean) sbomResult.get("sbomValidated"));
        assertNotNull(sbomResult.get("dependencies"));
        assertNotNull(sbomResult.get("vulnerabilityReport"));

        // Test image signing verification
        Map<String, Object> imageVerificationRequest = Map.of(
                "imageName", "ecm-identity-service:latest",
                "registry", "internal-registry.example.com",
                "expectedSignature", "sha256:mock-signature-hash"
        );

        ResponseEntity<Map> imageVerificationResponse = restTemplate.exchange(
                baseUrl + "/admin/security/image/verify",
                HttpMethod.POST,
                new HttpEntity<>(imageVerificationRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, imageVerificationResponse.getStatusCode());
        Map<String, Object> imageVerificationResult = imageVerificationResponse.getBody();
        assertTrue((Boolean) imageVerificationResult.get("signatureValid"));
        assertTrue((Boolean) imageVerificationResult.get("trustedSource"));

        // Verify comprehensive audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("secrets.vault.accessed")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("security.sbom.validated")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("security.image.signature.verified")));
    }

    @Test
    @DisplayName("NFR1.5 - Backup & Restore Token Safety")
    void testBackupAndRestoreTokenSafety() throws Exception {
        // Issue refresh tokens before backup
        List<String> preBackupTokens = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String token = issueRefreshToken("user-" + i + "@example.com");
            preBackupTokens.add(token);
        }

        // Simulate database backup
        HttpHeaders adminHeaders = createAdminHeaders();
        Map<String, Object> backupRequest = Map.of(
                "backupType", "full",
                "tenantCode", testTenant.getTenantCode(),
                "includeTokens", false // Tokens should not be backed up
        );

        ResponseEntity<Map> backupResponse = restTemplate.exchange(
                baseUrl + "/admin/backup/create",
                HttpMethod.POST,
                new HttpEntity<>(backupRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, backupResponse.getStatusCode());
        Map<String, Object> backupResult = backupResponse.getBody();
        assertNotNull(backupResult.get("backupId"));
        assertFalse((Boolean) backupResult.get("tokensIncluded"));

        String backupId = (String) backupResult.get("backupId");

        // Issue more tokens after backup
        List<String> postBackupTokens = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String token = issueRefreshToken("post-backup-user-" + i + "@example.com");
            postBackupTokens.add(token);
        }

        // Simulate database restore
        Map<String, Object> restoreRequest = Map.of(
                "backupId", backupId,
                "restoreType", "full",
                "invalidateZombieTokens", true
        );

        ResponseEntity<Map> restoreResponse = restTemplate.exchange(
                baseUrl + "/admin/backup/restore",
                HttpMethod.POST,
                new HttpEntity<>(restoreRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, restoreResponse.getStatusCode());
        Map<String, Object> restoreResult = restoreResponse.getBody();
        assertTrue((Boolean) restoreResult.get("restored"));
        assertTrue((Boolean) restoreResult.get("zombieTokensInvalidated"));
        assertTrue((Integer) restoreResult.get("invalidatedTokenCount") > 0);

        // Verify pre-backup tokens are invalidated (zombie tokens)
        for (String zombieToken : preBackupTokens) {
            Map<String, Object> tokenValidationRequest = Map.of(
                    "token", zombieToken
            );

            ResponseEntity<Map> validationResponse = restTemplate.exchange(
                    baseUrl + "/oauth2/token/validate",
                    HttpMethod.POST,
                    new HttpEntity<>(tokenValidationRequest),
                    Map.class
            );

            assertEquals(HttpStatus.UNAUTHORIZED, validationResponse.getStatusCode());
            Map<String, Object> validationResult = validationResponse.getBody();
            assertEquals("zombie_token", validationResult.get("error"));
        }

        // Verify post-backup tokens are also invalidated (for safety)
        for (String postBackupToken : postBackupTokens) {
            Map<String, Object> tokenValidationRequest = Map.of(
                    "token", postBackupToken
            );

            ResponseEntity<Map> validationResponse = restTemplate.exchange(
                    baseUrl + "/oauth2/token/validate",
                    HttpMethod.POST,
                    new HttpEntity<>(tokenValidationRequest),
                    Map.class
            );

            assertEquals(HttpStatus.UNAUTHORIZED, validationResponse.getStatusCode());
        }

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("backup.created.token_safe")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("restore.completed.zombie_tokens_invalidated")));
    }

    @Test
    @DisplayName("NFR1.8 - Cryptographic Key Lifecycle Policy")
    void testCryptographicKeyLifecyclePolicy() throws Exception {
        HttpHeaders adminHeaders = createAdminHeaders();

        // Create key with rotation policy
        Map<String, Object> keyCreationRequest = Map.of(
                "keyType", "RSA",
                "keySize", 2048,
                "purpose", "JWT_SIGNING",
                "tenantCode", testTenant.getTenantCode(),
                "rotationPolicy", Map.of(
                        "intervalDays", 90,
                        "dualSigningOverlapDays", 7,
                        "autoRotate", true
                )
        );

        ResponseEntity<Map> keyCreationResponse = restTemplate.exchange(
                baseUrl + "/admin/crypto/keys/create",
                HttpMethod.POST,
                new HttpEntity<>(keyCreationRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, keyCreationResponse.getStatusCode());
        Map<String, Object> keyResult = keyCreationResponse.getBody();
        assertNotNull(keyResult.get("keyId"));
        assertNotNull(keyResult.get("publicKey"));
        assertTrue((Boolean) keyResult.get("rotationPolicyEnabled"));

        String keyId = (String) keyResult.get("keyId");

        // Test manual key rotation with dual-signing overlap
        Map<String, Object> rotationRequest = Map.of(
                "keyId", keyId,
                "rotationType", "manual",
                "overlapPeriodDays", 7,
                "reason", "Scheduled quarterly rotation"
        );

        ResponseEntity<Map> rotationResponse = restTemplate.exchange(
                baseUrl + "/admin/crypto/keys/" + keyId + "/rotate",
                HttpMethod.POST,
                new HttpEntity<>(rotationRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, rotationResponse.getStatusCode());
        Map<String, Object> rotationResult = rotationResponse.getBody();
        assertTrue((Boolean) rotationResult.get("rotated"));
        assertNotNull(rotationResult.get("newKeyId"));
        assertNotNull(rotationResult.get("overlapPeriodEnd"));
        assertEquals("dual_signing_active", rotationResult.get("status"));

        String newKeyId = (String) rotationResult.get("newKeyId");

        // Test that both old and new keys work during overlap period
        String tokenWithOldKey = createTestTokenWithKey(keyId);
        String tokenWithNewKey = createTestTokenWithKey(newKeyId);

        // Both should validate successfully during overlap
        ResponseEntity<Map> oldKeyValidation = restTemplate.exchange(
                baseUrl + "/oauth2/token/validate",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("token", tokenWithOldKey)),
                Map.class
        );
        assertEquals(HttpStatus.OK, oldKeyValidation.getStatusCode());

        ResponseEntity<Map> newKeyValidation = restTemplate.exchange(
                baseUrl + "/oauth2/token/validate",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("token", tokenWithNewKey)),
                Map.class
        );
        assertEquals(HttpStatus.OK, newKeyValidation.getStatusCode());

        // Test key rotation history
        ResponseEntity<Map> historyResponse = restTemplate.exchange(
                baseUrl + "/admin/crypto/keys/" + keyId + "/rotation-history",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, historyResponse.getStatusCode());
        Map<String, Object> historyResult = historyResponse.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rotations = (List<Map<String, Object>>) historyResult.get("rotations");
        assertTrue(rotations.size() > 0);
        
        Map<String, Object> latestRotation = rotations.get(0);
        assertEquals("manual", latestRotation.get("rotationType"));
        assertEquals("Scheduled quarterly rotation", latestRotation.get("reason"));

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("crypto.key.created")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("crypto.key.rotated") &&
                event.getDescription().contains("dual_signing")));
    }

    @Test
    @DisplayName("NFR1.9 - Cryptographic Agility")
    void testCryptographicAgility() throws Exception {
        HttpHeaders adminHeaders = createAdminHeaders();

        // Test algorithm upgrade capability
        Map<String, Object> algorithmUpgradeRequest = Map.of(
                "currentAlgorithm", "RSA-2048",
                "targetAlgorithm", "RSA-4096",
                "upgradeType", "gradual_migration",
                "tenantCode", testTenant.getTenantCode(),
                "zeroDowntime", true
        );

        ResponseEntity<Map> upgradeResponse = restTemplate.exchange(
                baseUrl + "/admin/crypto/algorithm/upgrade",
                HttpMethod.POST,
                new HttpEntity<>(algorithmUpgradeRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, upgradeResponse.getStatusCode());
        Map<String, Object> upgradeResult = upgradeResponse.getBody();
        assertTrue((Boolean) upgradeResult.get("upgradeInitiated"));
        assertEquals("in_progress", upgradeResult.get("status"));
        assertNotNull(upgradeResult.get("migrationId"));

        // Test post-quantum readiness assessment
        ResponseEntity<Map> pqReadinessResponse = restTemplate.exchange(
                baseUrl + "/admin/crypto/post-quantum/readiness",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, pqReadinessResponse.getStatusCode());
        Map<String, Object> pqResult = pqReadinessResponse.getBody();
        assertNotNull(pqResult.get("readinessScore"));
        assertNotNull(pqResult.get("supportedPQAlgorithms"));
        assertNotNull(pqResult.get("migrationPlan"));

        // Test hybrid algorithm support (classical + post-quantum)
        Map<String, Object> hybridKeyRequest = Map.of(
                "keyType", "HYBRID",
                "classicalAlgorithm", "RSA-4096",
                "postQuantumAlgorithm", "CRYSTALS-Dilithium",
                "purpose", "FUTURE_PROOF_SIGNING"
        );

        ResponseEntity<Map> hybridKeyResponse = restTemplate.exchange(
                baseUrl + "/admin/crypto/keys/create-hybrid",
                HttpMethod.POST,
                new HttpEntity<>(hybridKeyRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, hybridKeyResponse.getStatusCode());
        Map<String, Object> hybridResult = hybridKeyResponse.getBody();
        assertNotNull(hybridResult.get("hybridKeyId"));
        assertTrue((Boolean) hybridResult.get("postQuantumReady"));
        assertNotNull(hybridResult.get("classicalComponent"));
        assertNotNull(hybridResult.get("postQuantumComponent"));

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("crypto.algorithm.upgrade.initiated")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("crypto.post_quantum.readiness.assessed")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("crypto.hybrid_key.created")));
    }

    // Helper methods
    private String authenticateUser(String email, String password) {
        return "mock-access-token-" + email.hashCode();
    }

    private HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("mock-admin-token");
        headers.set("X-Admin-Role", "security_admin");
        return headers;
    }

    private String issueRefreshToken(String userEmail) {
        return "refresh-token-" + userEmail.hashCode() + "-" + System.currentTimeMillis();
    }

    private String createTestTokenWithKey(String keyId) {
        return "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IiIgKyBrZXlJZCArICJ9." +
               "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9." +
               "mock-signature-" + keyId.hashCode();
    }
}

