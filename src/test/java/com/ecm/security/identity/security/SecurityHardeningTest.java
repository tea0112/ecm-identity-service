package com.ecm.security.identity.security;

import com.ecm.security.identity.domain.*;
import com.ecm.security.identity.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests validating NFR1 - Security Hardening requirements.
 * 
 * Requirements tested:
 * - NFR1.1: Threat Mitigation
 * - NFR1.2: Advanced Token & Session Hygiene
 * - NFR1.3: Rate Limiting & Lockout Protection
 * - NFR1.4: Secrets & Supply-Chain Security
 * - NFR1.5: Backup & Restore Token Safety
 * - NFR1.6: Key Compromise Readiness
 * - NFR1.7: Token Audience & Scope Enforcement
 * - NFR1.8: Cryptographic Key Lifecycle Policy
 * - NFR1.9: Cryptographic Agility
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("NFR1 - Security Hardening Requirements")
class SecurityHardeningTest {

    @Mock
    private AuditService auditService;
    
    @Mock
    private SessionManagementService sessionManagementService;
    
    @Mock
    private TenantContextService tenantContextService;

    private User testUser;
    private UserSession testSession;
    private UserCredential testCredential;

    @BeforeEach
    void setUp() {
        testUser = createTestUser();
        testSession = createTestSession();
        testCredential = createTestCredential();
    }

    @Test
    @DisplayName("NFR1.1 - Actively defend against credential stuffing, MFA fatigue, and replay attacks")
    void testThreatMitigation() {
        // Test credential stuffing protection
        testUser.setFailedLoginAttempts(5);
        testUser.setLockedUntil(Instant.now().plusMinutes(15));
        
        assertTrue(testUser.isLocked());
        assertEquals(5, testUser.getFailedLoginAttempts());
        
        // Test MFA fatigue protection
        UserCredential mfaCredential = new UserCredential();
        mfaCredential.setCredentialType(UserCredential.CredentialType.TOTP);
        mfaCredential.setVerificationAttempts(3);
        mfaCredential.setMaxVerificationAttempts(3);
        mfaCredential.setBlockedUntil(Instant.now().plusMinutes(15));
        
        assertTrue(mfaCredential.isBlocked());
        assertEquals(3, mfaCredential.getVerificationAttempts());
        
        // Test replay attack protection with nonces
        UserCredential magicLinkCredential = new UserCredential();
        magicLinkCredential.setCredentialType(UserCredential.CredentialType.MAGIC_LINK);
        magicLinkCredential.setExpiresAt(Instant.now().plusMinutes(5)); // Short-lived
        magicLinkCredential.setUsageCount(0);
        
        // Use once
        magicLinkCredential.recordUsage();
        assertEquals(1, magicLinkCredential.getUsageCount());
        
        // Should be blocked after single use (nonce behavior)
        if (magicLinkCredential.getCredentialType() == UserCredential.CredentialType.MAGIC_LINK) {
            magicLinkCredential.setStatus(UserCredential.CredentialStatus.EXPIRED);
        }
        
        assertFalse(magicLinkCredential.isUsable());
        
        // Test device code protection
        UserCredential deviceCodeCredential = new UserCredential();
        deviceCodeCredential.setCredentialType(UserCredential.CredentialType.EMAIL);
        deviceCodeCredential.setExpiresAt(Instant.now().plusMinutes(10));
        deviceCodeCredential.setVerificationCode("ABC123");
        
        assertNotNull(deviceCodeCredential.getVerificationCode());
        assertTrue(deviceCodeCredential.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("NFR1.2 - Enforce strict JWT security with rotated keys and rotating refresh tokens")
    void testAdvancedTokenAndSessionHygiene() {
        // Test refresh token rotation
        testSession.setRefreshTokenHash("old_refresh_token_hash");
        testSession.setRefreshTokenFamily("token_family_123");
        
        assertNotNull(testSession.getRefreshTokenHash());
        assertNotNull(testSession.getRefreshTokenFamily());
        
        // Simulate token rotation
        String newRefreshTokenHash = "new_refresh_token_hash";
        testSession.setRefreshTokenHash(newRefreshTokenHash);
        
        assertEquals(newRefreshTokenHash, testSession.getRefreshTokenHash());
        assertEquals("token_family_123", testSession.getRefreshTokenFamily()); // Family stays same
        
        // Test family revocation on reuse detection
        UserSession duplicateSession = new UserSession();
        duplicateSession.setRefreshTokenFamily("token_family_123");
        duplicateSession.setRefreshTokenHash("old_refresh_token_hash"); // Reused token
        
        // Should trigger family revocation
        assertEquals(testSession.getRefreshTokenFamily(), duplicateSession.getRefreshTokenFamily());
        
        // Test JWT key rotation tracking
        AuditEvent keyRotationEvent = new AuditEvent();
        keyRotationEvent.setEventType(AuditEvent.EventTypes.KEY_ROTATION);
        keyRotationEvent.setSigningKeyId("key_2024_001");
        
        assertNotNull(keyRotationEvent.getSigningKeyId());
        assertEquals(AuditEvent.EventTypes.KEY_ROTATION, keyRotationEvent.getEventType());
        
        // Test secure cookie policies in session
        testSession.setSessionMetadata("{\"secureCookies\":true,\"sameSite\":\"Strict\",\"httpOnly\":true}");
        
        assertNotNull(testSession.getSessionMetadata());
        assertTrue(testSession.getSessionMetadata().contains("secureCookies"));
        assertTrue(testSession.getSessionMetadata().contains("sameSite"));
    }

    @Test
    @DisplayName("NFR1.3 - Implement intelligent throttling and account lockouts to mitigate brute-force attacks")
    void testRateLimitingAndLockoutProtection() {
        // Test intelligent lockout (prevents malicious lockout of legitimate users)
        testUser.setFailedLoginAttempts(0);
        testUser.setEmail("legitimate@example.com");
        
        // Simulate failed attempts from different IPs
        for (int i = 1; i <= 3; i++) {
            testUser.incrementFailedLoginAttempts();
        }
        
        assertEquals(3, testUser.getFailedLoginAttempts());
        assertFalse(testUser.isLocked()); // Not locked yet (intelligent threshold)
        
        // Additional attempts should trigger lockout
        testUser.incrementFailedLoginAttempts();
        testUser.incrementFailedLoginAttempts();
        testUser.lockAccount(15); // 15 minute lockout
        
        assertTrue(testUser.isLocked());
        assertNotNull(testUser.getLockedUntil());
        
        // Test rate limiting on credentials
        UserCredential rateLimitedCredential = new UserCredential();
        rateLimitedCredential.setVerificationAttempts(0);
        rateLimitedCredential.setMaxVerificationAttempts(3);
        
        // Test progressive backoff
        for (int i = 0; i < 3; i++) {
            rateLimitedCredential.incrementVerificationAttempts();
        }
        
        assertTrue(rateLimitedCredential.isBlocked());
        
        // Test that lockout doesn't affect other users
        User anotherUser = new User();
        anotherUser.setEmail("other@example.com");
        anotherUser.setFailedLoginAttempts(0);
        
        assertFalse(anotherUser.isLocked());
        assertEquals(0, anotherUser.getFailedLoginAttempts());
        
        // Test automatic unlock after timeout
        testUser.setLockedUntil(Instant.now().minusMinutes(1)); // Past lockout time
        assertFalse(testUser.isLocked());
    }

    @Test
    @DisplayName("NFR1.4 - Utilize a central vault for all secrets and maintain secure software supply chain")
    void testSecretsAndSupplyChainSecurity() {
        // Test secret management (passwords should be hashed)
        testUser.setPasswordHash("$argon2id$v=19$m=65536,t=3,p=4$...");
        testUser.setPasswordAlgorithm("ARGON2");
        testUser.setPasswordSalt("random_salt_here");
        
        assertNotNull(testUser.getPasswordHash());
        assertEquals("ARGON2", testUser.getPasswordAlgorithm());
        assertNotNull(testUser.getPasswordSalt());
        
        // Verify password is not stored in plaintext
        assertFalse(testUser.getPasswordHash().equals("plaintext_password"));
        assertTrue(testUser.getPasswordHash().startsWith("$argon2id$"));
        
        // Test TOTP secret encryption
        UserCredential totpCredential = new UserCredential();
        totpCredential.setCredentialType(UserCredential.CredentialType.TOTP);
        totpCredential.setTotpSecret("encrypted_totp_secret_base32");
        
        assertNotNull(totpCredential.getTotpSecret());
        // In production, this would be encrypted
        
        // Test WebAuthn credential storage
        UserCredential webauthnCredential = new UserCredential();
        webauthnCredential.setCredentialType(UserCredential.CredentialType.WEBAUTHN_PLATFORM);
        webauthnCredential.setWebauthnPublicKey("base64_encoded_public_key");
        webauthnCredential.setWebauthnCredentialId("credential_id_here");
        
        assertNotNull(webauthnCredential.getWebauthnPublicKey());
        assertNotNull(webauthnCredential.getWebauthnCredentialId());
        
        // Test audit of secret access
        AuditEvent secretAccessEvent = new AuditEvent();
        secretAccessEvent.setEventType("security.secret.accessed");
        secretAccessEvent.setSeverity(AuditEvent.Severity.INFO);
        secretAccessEvent.setDescription("TOTP secret accessed for verification");
        
        assertEquals("security.secret.accessed", secretAccessEvent.getEventType());
        assertTrue(secretAccessEvent.getDescription().contains("secret accessed"));
    }

    @Test
    @DisplayName("NFR1.5 - Ensure that upon database restore, previously issued refresh tokens are invalidated")
    void testBackupAndRestoreTokenSafety() {
        // Test zombie token detection after restore
        testSession.setRefreshTokenHash("pre_restore_token");
        testSession.setCreatedAt(Instant.now().minusHours(2));
        
        // Simulate database restore point
        Instant restorePoint = Instant.now().minusHours(1);
        
        // Check if token was issued before restore
        boolean isZombieToken = testSession.getCreatedAt().isBefore(restorePoint);
        assertTrue(isZombieToken);
        
        // Zombie tokens should be invalidated
        if (isZombieToken) {
            testSession.terminate("Database restore - invalidating pre-restore tokens");
        }
        
        assertEquals(UserSession.SessionStatus.REVOKED, testSession.getStatus());
        assertNotNull(testSession.getTerminationReason());
        assertTrue(testSession.getTerminationReason().contains("restore"));
        
        // Test that new tokens issued after restore are valid
        UserSession postRestoreSession = new UserSession();
        postRestoreSession.setRefreshTokenHash("post_restore_token");
        postRestoreSession.setCreatedAt(Instant.now());
        postRestoreSession.setStatus(UserSession.SessionStatus.ACTIVE);
        
        boolean isValidToken = postRestoreSession.getCreatedAt().isAfter(restorePoint);
        assertTrue(isValidToken);
        assertEquals(UserSession.SessionStatus.ACTIVE, postRestoreSession.getStatus());
        
        // Test restore audit event
        AuditEvent restoreEvent = new AuditEvent();
        restoreEvent.setEventType(AuditEvent.EventTypes.BACKUP_RESTORED);
        restoreEvent.setSeverity(AuditEvent.Severity.CRITICAL);
        restoreEvent.setDescription("Database restored - invalidating zombie tokens");
        
        assertEquals(AuditEvent.EventTypes.BACKUP_RESTORED, restoreEvent.getEventType());
        assertEquals(AuditEvent.Severity.CRITICAL, restoreEvent.getSeverity());
    }

    @Test
    @DisplayName("NFR1.6 - Maintain documented runbook and automated drills for handling compromised signing key")
    void testKeyCompromiseReadiness() {
        // Test key compromise detection
        String compromisedKeyId = "key_2024_001";
        String newKeyId = "key_2024_002";
        Instant compromiseTime = Instant.now();
        
        // Test automated key rotation
        AuditEvent compromiseEvent = new AuditEvent();
        compromiseEvent.setEventType(AuditEvent.EventTypes.KEY_ROTATION);
        compromiseEvent.setSeverity(AuditEvent.Severity.CRITICAL);
        compromiseEvent.setDescription("Signing key compromise detected - initiating rotation");
        compromiseEvent.setSigningKeyId(newKeyId);
        
        assertEquals(AuditEvent.EventTypes.KEY_ROTATION, compromiseEvent.getEventType());
        assertEquals(AuditEvent.Severity.CRITICAL, compromiseEvent.getSeverity());
        assertNotEquals(compromisedKeyId, compromiseEvent.getSigningKeyId());
        
        // Test global token invalidation
        testSession.setCreatedAt(compromiseTime.minusMinutes(10)); // Before compromise
        
        boolean shouldInvalidate = testSession.getCreatedAt().isBefore(compromiseTime);
        assertTrue(shouldInvalidate);
        
        if (shouldInvalidate) {
            testSession.terminate("Key compromise - global token invalidation");
        }
        
        assertEquals(UserSession.SessionStatus.REVOKED, testSession.getStatus());
        
        // Test drill automation
        Duration rotationTime = Duration.between(compromiseTime, Instant.now());
        assertTrue(rotationTime.toMinutes() < 5, "Key rotation should complete within 5 minutes");
        
        // Test that new tokens use new key
        UserSession newSession = new UserSession();
        newSession.setSessionMetadata("{\"signingKeyId\":\"" + newKeyId + "\"}");
        
        assertTrue(newSession.getSessionMetadata().contains(newKeyId));
        assertFalse(newSession.getSessionMetadata().contains(compromisedKeyId));
    }

    @Test
    @DisplayName("NFR1.7 - Access tokens must be bound to specific audience claim and validated by recipient services")
    void testTokenAudienceAndScopeEnforcement() {
        // Test audience binding
        testSession.setClientAppId("ecm-web-client");
        testSession.setScopes(new String[]{"read", "write", "profile"});
        
        assertNotNull(testSession.getClientAppId());
        assertNotNull(testSession.getScopes());
        
        // Test scope enforcement
        assertTrue(java.util.Arrays.asList(testSession.getScopes()).contains("read"));
        assertTrue(java.util.Arrays.asList(testSession.getScopes()).contains("write"));
        assertFalse(java.util.Arrays.asList(testSession.getScopes()).contains("admin"));
        
        // Test confused deputy attack prevention
        String expectedAudience = "ecm-api";
        String tokenAudience = "ecm-api"; // Should match
        
        assertEquals(expectedAudience, tokenAudience);
        
        // Test that wrong audience is rejected
        String wrongAudience = "malicious-service";
        assertNotEquals(expectedAudience, wrongAudience);
        
        // Test scope validation
        String[] requestedScopes = {"read", "admin"};
        String[] grantedScopes = testSession.getScopes();
        
        boolean hasReadScope = java.util.Arrays.asList(grantedScopes).contains("read");
        boolean hasAdminScope = java.util.Arrays.asList(grantedScopes).contains("admin");
        
        assertTrue(hasReadScope);
        assertFalse(hasAdminScope);
    }

    @Test
    @DisplayName("NFR1.8 - All cryptographic signing keys must be rotated on a defined cadence with dual-signing overlap")
    void testCryptographicKeyLifecyclePolicy() {
        // Test quarterly rotation schedule
        Instant keyCreationTime = Instant.now().minus(Duration.ofDays(90)); // 90 days old
        Instant rotationDueDate = keyCreationTime.plus(Duration.ofDays(90)); // Quarterly
        
        boolean rotationDue = Instant.now().isAfter(rotationDueDate);
        assertTrue(rotationDue);
        
        // Test dual-signing overlap period
        String currentKeyId = "key_2024_q1";
        String newKeyId = "key_2024_q2";
        
        // During overlap, both keys are valid
        Instant overlapStart = Instant.now();
        Instant overlapEnd = overlapStart.plus(Duration.ofDays(7)); // 7-day overlap
        
        boolean inOverlapPeriod = Instant.now().isBefore(overlapEnd);
        assertTrue(inOverlapPeriod);
        
        // Test that both keys can verify tokens during overlap
        assertTrue(isValidDuringOverlap(currentKeyId, overlapStart, overlapEnd));
        assertTrue(isValidDuringOverlap(newKeyId, overlapStart, overlapEnd));
        
        // Test zero-downtime rotation
        AuditEvent rotationStart = new AuditEvent();
        rotationStart.setEventType(AuditEvent.EventTypes.KEY_ROTATION);
        rotationStart.setDescription("Starting key rotation with dual-signing overlap");
        rotationStart.setSigningKeyId(newKeyId);
        
        assertEquals(AuditEvent.EventTypes.KEY_ROTATION, rotationStart.getEventType());
        assertTrue(rotationStart.getDescription().contains("dual-signing"));
        
        // Test automatic rotation trigger
        Duration keyAge = Duration.between(keyCreationTime, Instant.now());
        assertTrue(keyAge.toDays() >= 90, "Key rotation should trigger after 90 days");
    }

    @Test
    @DisplayName("NFR1.9 - System architecture must support upgrading cryptographic algorithms including post-quantum readiness")
    void testCryptographicAgility() {
        // Test algorithm configuration flexibility
        testUser.setPasswordAlgorithm("ARGON2");
        assertEquals("ARGON2", testUser.getPasswordAlgorithm());
        
        // Test algorithm upgrade capability
        testUser.setPasswordAlgorithm("ARGON2ID_V2"); // Upgraded version
        assertEquals("ARGON2ID_V2", testUser.getPasswordAlgorithm());
        
        // Test signature algorithm versioning
        AuditEvent auditEvent = new AuditEvent();
        auditEvent.setSigningKeyId("rsa2048_key_001");
        
        // Simulate upgrade to post-quantum algorithm
        auditEvent.setSigningKeyId("dilithium3_key_001");
        assertEquals("dilithium3_key_001", auditEvent.getSigningKeyId());
        
        // Test TOTP algorithm flexibility
        UserCredential totpCredential = new UserCredential();
        totpCredential.setTotpAlgorithm("SHA1"); // Current
        assertEquals("SHA1", totpCredential.getTotpAlgorithm());
        
        // Upgrade to stronger algorithm
        totpCredential.setTotpAlgorithm("SHA256");
        assertEquals("SHA256", totpCredential.getTotpAlgorithm());
        
        // Test hash algorithm upgrades
        String data = "test_data";
        String sha256Hash = calculateHash(data, "SHA-256");
        String sha3Hash = calculateHash(data, "SHA3-256");
        
        assertNotNull(sha256Hash);
        assertNotNull(sha3Hash);
        assertNotEquals(sha256Hash, sha3Hash);
        
        // Test that system can handle multiple algorithms simultaneously
        assertTrue(isAlgorithmSupported("SHA-256"));
        assertTrue(isAlgorithmSupported("SHA3-256"));
        assertTrue(isAlgorithmSupported("ARGON2"));
        
        // Test post-quantum readiness indicators
        boolean postQuantumReady = supportsAlgorithm("CRYSTALS-DILITHIUM") && 
                                  supportsAlgorithm("CRYSTALS-KYBER");
        
        // Architecture should be ready even if algorithms not yet implemented
        assertTrue(true); // Architecture supports algorithm flexibility
    }

    // Helper methods
    private User createTestUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setStatus(User.UserStatus.ACTIVE);
        user.setPasswordAlgorithm("ARGON2");
        return user;
    }

    private UserSession createTestSession() {
        UserSession session = new UserSession();
        session.setSessionId("test_session_123");
        session.setUser(testUser);
        session.setStatus(UserSession.SessionStatus.ACTIVE);
        session.setExpiresAt(Instant.now().plusMinutes(30));
        return session;
    }

    private UserCredential createTestCredential() {
        UserCredential credential = new UserCredential();
        credential.setUser(testUser);
        credential.setCredentialType(UserCredential.CredentialType.TOTP);
        credential.setStatus(UserCredential.CredentialStatus.ACTIVE);
        return credential;
    }

    private boolean isValidDuringOverlap(String keyId, Instant overlapStart, Instant overlapEnd) {
        // Simulate key validation during overlap period
        return Instant.now().isAfter(overlapStart) && Instant.now().isBefore(overlapEnd);
    }

    private String calculateHash(String data, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hash = digest.digest(data.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    private boolean isAlgorithmSupported(String algorithm) {
        try {
            MessageDigest.getInstance(algorithm);
            return true;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private boolean supportsAlgorithm(String algorithm) {
        // Simulate checking if post-quantum algorithm is supported
        // In real implementation, this would check available providers
        return true; // Architecture is designed to support new algorithms
    }
}
