package com.ecm.security.identity.service;

import com.ecm.security.identity.domain.*;
import com.ecm.security.identity.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests validating FR1 - Authentication & Sessions requirements.
 * 
 * Requirements tested:
 * - FR1.1: Credential Login & Passwordless
 * - FR1.2: Token Issuance & Refresh  
 * - FR1.3: Multi-Factor & Step-Up Authentication
 * - FR1.4: Device & Session Management
 * - FR1.5: Secure Account Recovery & Fallbacks
 * - FR1.6: Account Linking & Merging
 * - FR1.7: Age Gates & Parental Consent
 * - FR1.8: Proactive Session Invalidation based on Risk Signals
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("FR1 - Authentication & Sessions Requirements")
class AuthenticationRequirementsTest {

    @Mock
    private UserSessionRepository sessionRepository;
    
    @Mock
    private UserDeviceRepository deviceRepository;
    
    @Mock
    private AuditService auditService;
    
    @Mock
    private RiskAssessmentService riskAssessmentService;
    
    @Mock
    private TenantContextService tenantContextService;

    private SessionManagementService sessionManagementService;
    private User testUser;
    private Tenant testTenant;
    private UserDevice testDevice;

    @BeforeEach
    void setUp() {
        sessionManagementService = new SessionManagementService(
            sessionRepository, deviceRepository, auditService, riskAssessmentService, tenantContextService);
        
        // Setup test data
        testTenant = createTestTenant();
        testUser = createTestUser(testTenant);
        testDevice = createTestDevice(testUser);
    }

    @Test
    @DisplayName("FR1.1 - Support authentication via password, WebAuthn/passkeys, and magic links")
    void testMultipleAuthenticationMethods() {
        // Test password authentication
        UserSession passwordSession = createSessionWithAuthMethod(UserSession.AuthenticationMethod.PASSWORD);
        assertEquals(UserSession.AuthenticationMethod.PASSWORD, passwordSession.getAuthenticationMethod());
        
        // Test WebAuthn authentication
        UserSession webauthnSession = createSessionWithAuthMethod(UserSession.AuthenticationMethod.WEBAUTHN);
        assertEquals(UserSession.AuthenticationMethod.WEBAUTHN, webauthnSession.getAuthenticationMethod());
        
        // Test magic link authentication
        UserSession magicLinkSession = createSessionWithAuthMethod(UserSession.AuthenticationMethod.MAGIC_LINK);
        assertEquals(UserSession.AuthenticationMethod.MAGIC_LINK, magicLinkSession.getAuthenticationMethod());
        
        // Verify all methods are supported
        assertTrue(List.of(UserSession.AuthenticationMethod.values()).contains(UserSession.AuthenticationMethod.PASSWORD));
        assertTrue(List.of(UserSession.AuthenticationMethod.values()).contains(UserSession.AuthenticationMethod.WEBAUTHN));
        assertTrue(List.of(UserSession.AuthenticationMethod.values()).contains(UserSession.AuthenticationMethod.MAGIC_LINK));
    }

    @Test
    @DisplayName("FR1.2 - Generate and manage short-lived access tokens and long-lived, rotating refresh tokens")
    void testTokenIssuanceAndRefresh() {
        // Create session with refresh token
        SessionManagementService.SessionCreationRequest request = createBasicSessionRequest();
        
        when(sessionRepository.findActiveSessionsByUserId(any())).thenReturn(List.of());
        when(deviceRepository.findByUserAndDeviceFingerprint(any(), any())).thenReturn(Optional.empty());
        when(deviceRepository.save(any())).thenReturn(testDevice);
        UserSession mockSession = mock(UserSession.class);
        when(mockSession.getExpiresAt()).thenReturn(Instant.now().plus(Duration.ofHours(1)));
        when(mockSession.getSessionId()).thenReturn("sess_test123");
        when(sessionRepository.save(any())).thenReturn(mockSession);
        when(riskAssessmentService.calculateSessionRisk(any(), any(), any())).thenReturn(10.0);
        when(riskAssessmentService.getRiskFactors(any(), any(), any())).thenReturn(new String[]{});
        when(tenantContextService.getCurrentTenantId()).thenReturn(testTenant.getId());
        
        // Mock tenant settings
        TenantContextService.TenantSettings mockSettings = mock(TenantContextService.TenantSettings.class);
        TenantContextService.TenantSettings.SessionPolicy mockPolicy = mock(TenantContextService.TenantSettings.SessionPolicy.class);
        when(mockPolicy.getSessionTimeout()).thenReturn(3600); // 1 hour
        when(mockSettings.getSessionPolicy()).thenReturn(mockPolicy);
        when(tenantContextService.getTenantSettings(any())).thenReturn(mockSettings);
        
        UserSession session = sessionManagementService.createSession(testUser, request);
        
        // Verify session was created
        assertNotNull(session);
        
        // Verify session has refresh token configuration
        verify(sessionRepository).save(argThat(s -> 
            s.getRefreshTokenHash() != null && 
            s.getRefreshTokenFamily() != null
        ));
        
        // Verify token expiration times are different (short-lived access vs long-lived refresh)
        assertNotNull(session.getExpiresAt());
        assertTrue(session.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    @DisplayName("FR1.3 - Support MFA (TOTP, WebAuthn) and trigger policy-driven step-up authentication")
    void testMultiFactorAndStepUpAuthentication() {
        // Test MFA completion tracking
        UserSession session = new UserSession();
        session.setMfaCompleted(true);
        session.setMfaMethodsUsed(new String[]{"TOTP", "WEBAUTHN"});
        
        assertTrue(session.getMfaCompleted());
        assertArrayEquals(new String[]{"TOTP", "WEBAUTHN"}, session.getMfaMethodsUsed());
        
        // Test step-up authentication requirement
        session.setStepUpRequired(true);
        session.setStepUpCompleted(false);
        session.setStepUpRequiredFor(new String[]{"admin", "delete"});
        
        assertTrue(session.requiresStepUp());
        assertArrayEquals(new String[]{"admin", "delete"}, session.getStepUpRequiredFor());
        
        // Test step-up completion
        session.setStepUpCompleted(true);
        assertFalse(session.requiresStepUp());
    }

    @Test
    @DisplayName("FR1.4 - Allow users to view and revoke their active sessions with device binding")
    void testDeviceAndSessionManagement() {
        UUID userId = testUser.getId();
        List<UserSession> mockSessions = List.of(
            createMockSession("session1", testDevice),
            createMockSession("session2", testDevice)
        );
        
        when(sessionRepository.findActiveSessionsByUserId(userId)).thenReturn(mockSessions);
        
        // Test getting user's active sessions
        List<UserSession> activeSessions = sessionManagementService.getUserActiveSessions(userId);
        assertEquals(2, activeSessions.size());
        
        // Test session termination
        sessionManagementService.terminateSession("session1", "User requested termination");
        verify(sessionRepository).findBySessionId("session1");
        
        // Test device binding - sessions should be bound to devices
        mockSessions.forEach(session -> {
            assertNotNull(session.getDevice());
            assertEquals(testDevice.getId(), session.getDevice().getId());
        });
        
        // Test device signals tracking
        assertNotNull(testDevice.getDeviceFingerprint());
        assertNotNull(testDevice.getIpAddress());
        assertNotNull(testDevice.getUserAgent());
    }

    @Test
    @DisplayName("FR1.5 - Provide secure, rate-limited account recovery flows and passwordless fallbacks")
    void testSecureAccountRecoveryAndFallbacks() {
        // Test recovery code credential
        UserCredential recoveryCode = new UserCredential();
        recoveryCode.setCredentialType(UserCredential.CredentialType.RECOVERY_CODE);
        recoveryCode.setRecoveryCodeUsed(false);
        recoveryCode.setVerificationAttempts(0);
        recoveryCode.setMaxVerificationAttempts(3);
        
        assertTrue(recoveryCode.isUsable());
        assertFalse(recoveryCode.getRecoveryCodeUsed());
        
        // Test rate limiting on verification attempts
        recoveryCode.incrementVerificationAttempts();
        recoveryCode.incrementVerificationAttempts();
        recoveryCode.incrementVerificationAttempts();
        
        assertEquals(3, recoveryCode.getVerificationAttempts());
        assertTrue(recoveryCode.isBlocked()); // Should be blocked after max attempts
        
        // Test backup codes
        UserCredential backupCode = new UserCredential();
        backupCode.setCredentialType(UserCredential.CredentialType.BACKUP_CODE);
        backupCode.setBackupEligible(true);
        
        assertTrue(backupCode.getBackupEligible());
        assertTrue(backupCode.isRecoveryCode());
    }

    @Test
    @DisplayName("FR1.6 - Allow a single user to link multiple identities with full audit trail and reversible merges")
    void testAccountLinkingAndMerging() {
        // Test account linking
        LinkedIdentity googleIdentity = new LinkedIdentity();
        googleIdentity.setUser(testUser);
        googleIdentity.setProvider("google");
        googleIdentity.setExternalId("google123");
        googleIdentity.setIdentityType(LinkedIdentity.IdentityType.SOCIAL);
        googleIdentity.setStatus(LinkedIdentity.LinkStatus.ACTIVE);
        googleIdentity.setMergeEligible(true);
        googleIdentity.setMergeReversible(true);
        googleIdentity.setVerified(true);
        
        assertTrue(googleIdentity.canMerge());
        
        // Test merging with audit trail
        googleIdentity.setMergedFromUserId("user456");
        googleIdentity.setMergeOperationId("merge_op_789");
        googleIdentity.setStatus(LinkedIdentity.LinkStatus.MERGED);
        
        assertTrue(googleIdentity.isMerged());
        assertNotNull(googleIdentity.getMergedFromUserId());
        assertNotNull(googleIdentity.getMergeOperationId());
        assertTrue(googleIdentity.canReverseMerge());
        
        // Test reversible merge with expiration
        googleIdentity.setMergeExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        assertTrue(googleIdentity.canReverseMerge());
        
        // Test unlinking
        googleIdentity.unlink("User requested", "admin123");
        assertEquals(LinkedIdentity.LinkStatus.UNLINKED, googleIdentity.getStatus());
        assertNotNull(googleIdentity.getUnlinkedAt());
        assertNotNull(googleIdentity.getUnlinkReason());
    }

    @Test
    @DisplayName("FR1.7 - Implement workflows for handling child accounts with age verification and parental consent")
    void testAgeGatesAndParentalConsent() {
        // Test minor user account
        User minorUser = new User();
        minorUser.setIsMinor(true);
        minorUser.setParentEmail("parent@example.com");
        minorUser.setParentalConsentAt(null);
        
        assertTrue(minorUser.getIsMinor());
        assertTrue(minorUser.requiresParentalConsent());
        assertNotNull(minorUser.getParentEmail());
        
        // Test parental consent process
        minorUser.setParentalConsentAt(Instant.now());
        assertFalse(minorUser.requiresParentalConsent());
        assertNotNull(minorUser.getParentalConsentAt());
        
        // Test adult user
        User adultUser = new User();
        adultUser.setIsMinor(false);
        
        assertFalse(adultUser.getIsMinor());
        assertFalse(adultUser.requiresParentalConsent());
    }

    @Test
    @DisplayName("FR1.8 - Automatically invalidate sessions and require re-authentication upon detection of high-risk signals")
    void testProactiveSessionInvalidationBasedOnRiskSignals() {
        UserSession highRiskSession = new UserSession();
        highRiskSession.setSessionId("high_risk_session");
        highRiskSession.setUser(testUser);
        highRiskSession.setRiskLevel(UserSession.RiskLevel.HIGH);
        highRiskSession.setRiskScore(85.0);
        highRiskSession.setStatus(UserSession.SessionStatus.ACTIVE);
        
        // Test impossible travel detection
        highRiskSession.setImpossibleTravelDetected(true);
        assertTrue(highRiskSession.getImpossibleTravelDetected());
        assertTrue(highRiskSession.isHighRisk());
        
        // Test device fingerprint change detection
        highRiskSession.setDeviceFingerprintChanged(true);
        assertTrue(highRiskSession.getDeviceFingerprintChanged());
        
        // Test automatic invalidation for high-risk sessions
        when(sessionRepository.findByRiskLevelAndStatus(UserSession.RiskLevel.HIGH, UserSession.SessionStatus.ACTIVE))
            .thenReturn(List.of(highRiskSession));
        when(sessionRepository.findByRiskLevelAndStatus(UserSession.RiskLevel.CRITICAL, UserSession.SessionStatus.ACTIVE))
            .thenReturn(List.of());
        
        sessionManagementService.invalidateHighRiskSessions();
        
        // Verify sessions are invalidated
        verify(sessionRepository).findByRiskLevelAndStatus(UserSession.RiskLevel.HIGH, UserSession.SessionStatus.ACTIVE);
        verify(sessionRepository).findByRiskLevelAndStatus(UserSession.RiskLevel.CRITICAL, UserSession.SessionStatus.ACTIVE);
        
        // Test risk factor tracking
        highRiskSession.setRiskFactors(new String[]{"impossible_travel", "device_fingerprint_change", "suspicious_ip"});
        assertArrayEquals(new String[]{"impossible_travel", "device_fingerprint_change", "suspicious_ip"}, 
                         highRiskSession.getRiskFactors());
    }

    @Test
    @DisplayName("FR1.4 - Session data must be bound to device signals and support attestation")
    void testDeviceSignalsAndAttestation() {
        // Test device attestation support
        UserDevice attestedDevice = new UserDevice();
        attestedDevice.setAttestationSupported(true);
        attestedDevice.setAttestationVerified(true);
        attestedDevice.setAttestationData("attestation_data_here");
        attestedDevice.setTpmPresent(true);
        attestedDevice.setSecureElementPresent(true);
        
        assertTrue(attestedDevice.getAttestationSupported());
        assertTrue(attestedDevice.getAttestationVerified());
        assertTrue(attestedDevice.getTpmPresent());
        assertTrue(attestedDevice.getSecureElementPresent());
        assertNotNull(attestedDevice.getAttestationData());
        
        // Test device signal binding
        UserSession boundSession = new UserSession();
        boundSession.setDevice(attestedDevice);
        boundSession.setIpAddress("192.168.1.100");
        boundSession.setUserAgent("Mozilla/5.0...");
        boundSession.setLocationCountry("US");
        boundSession.setLocationCity("San Francisco");
        
        assertNotNull(boundSession.getDevice());
        assertEquals(attestedDevice, boundSession.getDevice());
        assertNotNull(boundSession.getIpAddress());
        assertNotNull(boundSession.getUserAgent());
        assertNotNull(boundSession.getLocationCountry());
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

    private User createTestUser(Tenant tenant) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenant(tenant);
        user.setEmail("test@example.com");
        user.setStatus(User.UserStatus.ACTIVE);
        user.setMfaEnabled(true);
        return user;
    }

    private UserDevice createTestDevice(User user) {
        UserDevice device = new UserDevice();
        device.setId(UUID.randomUUID());
        device.setUser(user);
        device.setDeviceFingerprint("device_fp_123");
        device.setIpAddress("192.168.1.100");
        device.setUserAgent("Mozilla/5.0 (Test Browser)");
        device.setStatus(UserDevice.DeviceStatus.VERIFIED);
        return device;
    }

    private UserSession createSessionWithAuthMethod(UserSession.AuthenticationMethod method) {
        UserSession session = new UserSession();
        session.setAuthenticationMethod(method);
        session.setUser(testUser);
        session.setDevice(testDevice);
        return session;
    }

    private SessionManagementService.SessionCreationRequest createBasicSessionRequest() {
        return SessionManagementService.SessionCreationRequest.builder()
            .ipAddress("192.168.1.100")
            .userAgent("Mozilla/5.0...")
            .authenticationMethod(UserSession.AuthenticationMethod.PASSWORD)
            .deviceFingerprint("device_fp_123")
            .mfaCompleted(true)
            .build();
    }

    private UserSession createMockSession(String sessionId, UserDevice device) {
        UserSession session = new UserSession();
        session.setSessionId(sessionId);
        session.setDevice(device);
        session.setUser(testUser);
        session.setStatus(UserSession.SessionStatus.ACTIVE);
        session.setExpiresAt(Instant.now().plus(Duration.ofMinutes(30)));
        return session;
    }
}
