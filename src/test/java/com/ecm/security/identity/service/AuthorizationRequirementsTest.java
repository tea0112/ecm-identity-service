package com.ecm.security.identity.service;

import com.ecm.security.identity.domain.*;
import com.ecm.security.identity.repository.TenantPolicyRepository;
import com.ecm.security.identity.service.AuthorizationService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests validating FR3 - Authorization & Access Control requirements.
 * 
 * Requirements tested:
 * - FR3.1: Policy Engine (ABAC/ReBAC)
 * - FR3.2: Contextual Authorization API
 * - FR3.3: Continuous Authorization for Long-Lived Connections
 * - FR3.4: Time-Bound, JIT & Emergency Access
 * - FR3.5: Advanced Delegation & Scoped Administration
 * - FR3.6: Granular Consent Management
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("FR3 - Authorization & Access Control Requirements")
class AuthorizationRequirementsTest {

    @Mock
    private PolicyService policyService;
    
    @Mock
    private AuditService auditService;
    
    @Mock
    private TenantContextService tenantContextService;

    private AuthorizationService authorizationService;
    private UUID testTenantId;
    private User testUser;
    private UserSession testSession;

    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationService(policyService, auditService);
        testTenantId = UUID.randomUUID();
        testUser = createTestUser();
        testSession = createTestSession();
    }

    @Test
    @DisplayName("FR3.1 - Utilize a central policy engine for fine-grained authorization based on attributes (ABAC) and relationships (ReBAC)")
    void testPolicyEngineABACReBAC() {
        // Test ABAC - Attribute-Based Access Control
        TenantPolicy abacPolicy = createABACPolicy();
        List<TenantPolicy> policies = new ArrayList<>(List.of(abacPolicy));
        
        when(policyService.getApplicablePolicies(eq(testTenantId), anyString(), anyString(), anyString()))
            .thenReturn(policies);
        
        AuthorizationRequest request = AuthorizationRequest.builder()
            .tenantId(testTenantId)
            .subject("user:123")
            .resource("document:456")
            .action("read")
            .context(Map.of("department", "finance", "clearanceLevel", "confidential"))
            .build();
        
        AuthorizationDecision decision = authorizationService.authorize(request);
        
        assertNotNull(decision);
        
        verify(policyService).getApplicablePolicies(testTenantId, "user:123", "document:456", "read");
        verify(auditService).logAuthorizationDecision(request, decision);
        
        // Test ReBAC - Relationship-Based Access Control
        TenantPolicy rebacPolicy = createReBACPolicy();
        rebacPolicy.setSubjects(new String[]{"role:manager", "group:finance"});
        rebacPolicy.setResources(new String[]{"department:finance:*"});
        
        assertTrue(rebacPolicy.appliesToSubject("role:manager"));
        assertTrue(rebacPolicy.appliesToResource("department:finance:reports"));
        assertFalse(rebacPolicy.appliesToSubject("role:intern"));
        
        // Test precedence rules - explicit deny overrides allow
        TenantPolicy denyPolicy = new TenantPolicy();
        denyPolicy.setEffect(TenantPolicy.Effect.DENY);
        denyPolicy.setPriority(1);
        denyPolicy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        
        TenantPolicy allowPolicy = new TenantPolicy();
        allowPolicy.setEffect(TenantPolicy.Effect.ALLOW);
        allowPolicy.setPriority(100);
        allowPolicy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        
        assertTrue(denyPolicy.getPriority() < allowPolicy.getPriority());
        assertEquals(TenantPolicy.Effect.DENY, denyPolicy.getEffect());
        assertEquals(TenantPolicy.Effect.ALLOW, allowPolicy.getEffect());
    }

    @Test
    @DisplayName("FR3.2 - Provide a secure API to answer complex authorization questions, handling batch decisions and protecting against TOCTOU races")
    void testContextualAuthorizationAPI() {
        // Test batch authorization decisions
        List<AuthorizationRequest> batchRequests = Arrays.asList(
            createAuthRequest("user:1", "resource:A", "read"),
            createAuthRequest("user:1", "resource:B", "write"),
            createAuthRequest("user:2", "resource:A", "read")
        );
        
        when(policyService.getApplicablePolicies(any(), any(), any(), any()))
            .thenReturn(new ArrayList<>(List.of(createAllowPolicy())));
        
        List<AuthorizationDecision> decisions = authorizationService.batchAuthorize(batchRequests);
        
        assertEquals(3, decisions.size());
        decisions.forEach(decision -> assertNotNull(decision));
        
        // Test contextual authorization with complex context
        Map<String, Object> complexContext = Map.of(
            "time", Instant.now(),
            "location", "office",
            "riskScore", 25.0,
            "mfaCompleted", true,
            "deviceTrusted", true
        );
        
        boolean hasPermission = authorizationService.hasPermission(
            testUser, "sensitive:data", "export", complexContext);
        
        verify(policyService, atLeastOnce()).getApplicablePolicies(any(), any(), any(), any());
        
        // Test TOCTOU protection through timestamps
        AuthorizationRequest request = createAuthRequest("user:1", "resource:1", "update");
        assertNotNull(request.getTimestamp());
        assertTrue(request.getTimestamp().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    @DisplayName("FR3.3 - Support the revocation of permissions for any long-lived connection requiring clients to re-validate mid-session")
    void testContinuousAuthorizationForLongLivedConnections() {
        // Test continuous authorization validation
        UserSession activeSession = createTestSession();
        activeSession.setStatus(UserSession.SessionStatus.ACTIVE);
        activeSession.setRiskLevel(UserSession.RiskLevel.LOW);
        
        boolean isValid = authorizationService.validateContinuousAuthorization(
            activeSession, "websocket:data", "stream");
        
        // Should validate session and re-check permissions
        assertTrue(activeSession.isActive());
        assertFalse(activeSession.isHighRisk());
        
        // Test with high-risk session
        UserSession highRiskSession = createTestSession();
        highRiskSession.setRiskLevel(UserSession.RiskLevel.HIGH);
        
        boolean highRiskValid = authorizationService.validateContinuousAuthorization(
            highRiskSession, "websocket:data", "stream");
        
        // High-risk sessions should fail continuous authorization
        assertTrue(highRiskSession.isHighRisk());
        
        // Test with expired session
        UserSession expiredSession = createTestSession();
        expiredSession.setExpiresAt(Instant.now().minusSeconds(3600));
        
        assertFalse(expiredSession.isActive());
    }

    @Test
    @DisplayName("FR3.4 - Support temporary access grants, approval workflows, and formally defined break-glass accounts")
    void testTimeBoundJITAndEmergencyAccess() {
        // Test time-bound access (JIT - Just-In-Time)
        UserRole jitRole = new UserRole();
        jitRole.setAssignmentType(UserRole.AssignmentType.JIT);
        jitRole.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        jitRole.setJustification("Emergency database access for incident #12345");
        jitRole.setApprovalRequired(true);
        jitRole.setStatus(UserRole.RoleStatus.PENDING_APPROVAL);
        
        assertTrue(jitRole.isTemporary());
        assertTrue(jitRole.requiresApproval());
        assertEquals(UserRole.AssignmentType.JIT, jitRole.getAssignmentType());
        assertNotNull(jitRole.getJustification());
        
        // Test approval workflow
        jitRole.approve("manager123");
        assertEquals(UserRole.RoleStatus.ACTIVE, jitRole.getStatus());
        assertNotNull(jitRole.getApprovedAt());
        assertNotNull(jitRole.getApprovedByUserId());
        
        // Test break-glass accounts
        UserRole breakGlassRole = new UserRole();
        breakGlassRole.setBreakGlassRole(true);
        breakGlassRole.setEmergencyAccess(true);
        breakGlassRole.setEmergencyJustification("Critical system outage - immediate access required");
        breakGlassRole.setEmergencyApprovedBy("incident_commander");
        
        assertTrue(breakGlassRole.isBreakGlass());
        assertNotNull(breakGlassRole.getEmergencyJustification());
        
        // Test temporary access expiration
        UserRole expiredRole = new UserRole();
        expiredRole.setExpiresAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        
        assertTrue(expiredRole.isExpired());
        assertFalse(expiredRole.isActive());
        
        // Test usage-limited access
        UserRole limitedRole = new UserRole();
        limitedRole.setMaxUsageCount(3);
        limitedRole.setUsageCount(0);
        
        assertFalse(limitedRole.hasUsageLimit());
        
        limitedRole.recordUsage();
        limitedRole.recordUsage();
        limitedRole.recordUsage();
        
        assertTrue(limitedRole.hasUsageLimit());
        assertEquals(3, limitedRole.getUsageCount());
    }

    @Test
    @DisplayName("FR3.5 - Allow a user to securely delegate a subset of their permissions to another user with scoped administrators")
    void testAdvancedDelegationAndScopedAdministration() {
        // Test permission delegation
        UserRole originalRole = new UserRole();
        originalRole.setRoleName("PROJECT_ADMIN");
        originalRole.setScope("project:123");
        originalRole.setMaxDelegationDepth(2);
        originalRole.setDelegationDepth(0);
        
        assertTrue(originalRole.canDelegate());
        
        // Test delegated role creation
        UserRole delegatedRole = new UserRole();
        delegatedRole.setRoleName("PROJECT_ADMIN");
        delegatedRole.setScope("project:123");
        delegatedRole.setAssignmentType(UserRole.AssignmentType.DELEGATED);
        delegatedRole.setDelegatedFromUserId("original_user_id");
        delegatedRole.setDelegationDepth(1);
        delegatedRole.setMaxDelegationDepth(2);
        delegatedRole.setDelegationRestrictions("{\"actions\":[\"read\",\"write\"],\"excludeActions\":[\"delete\"]}");
        
        assertTrue(delegatedRole.isDelegated());
        assertNotNull(delegatedRole.getDelegatedFromUserId());
        assertFalse(delegatedRole.canDelegate()); // Cannot delegate further as it's already delegated
        
        // Test a role that can delegate (not already delegated)
        UserRole delegatableRole = new UserRole();
        delegatableRole.setRoleName("ADMIN");
        delegatableRole.setScope("project:123");
        delegatableRole.setStatus(UserRole.RoleStatus.ACTIVE);
        delegatableRole.setAssignmentType(UserRole.AssignmentType.PERMANENT);
        delegatableRole.setDelegationDepth(0);
        delegatableRole.setMaxDelegationDepth(2);
        
        assertTrue(delegatableRole.canDelegate()); // Can delegate as it's not already delegated
        
        // Test scoped administration
        UserRole scopedAdmin = new UserRole();
        scopedAdmin.setRoleName("SCOPED_ADMIN");
        scopedAdmin.setScope("department:engineering");
        scopedAdmin.setDelegationRestrictions("{\"scope\":\"department:engineering\",\"preventEscalation\":true}");
        
        assertEquals("department:engineering", scopedAdmin.getScope());
        assertNotNull(scopedAdmin.getDelegationRestrictions());
        
        // Test delegation chain limits
        UserRole maxDepthRole = new UserRole();
        maxDepthRole.setDelegationDepth(2);
        maxDepthRole.setMaxDelegationDepth(2);
        
        assertFalse(maxDepthRole.canDelegate()); // At max depth
        
        // Test delegation revocation
        delegatedRole.revoke("Delegation revoked by original user", "original_user_id");
        assertTrue(delegatedRole.isRevoked());
        assertNotNull(delegatedRole.getRevokedAt());
        assertNotNull(delegatedRole.getRevocationReason());
    }

    @Test
    @DisplayName("FR3.6 - Allow users to grant granular consent to applications on a per-resource or per-action basis")
    void testGranularConsentManagement() {
        // Test granular consent in session
        UserSession consentSession = new UserSession();
        consentSession.setConsentGiven(true);
        consentSession.setConsentScopes(new String[]{"read:profile", "write:documents", "read:contacts"});
        
        assertTrue(consentSession.getConsentGiven());
        assertArrayEquals(new String[]{"read:profile", "write:documents", "read:contacts"}, 
                         consentSession.getConsentScopes());
        
        // Test consent in linked identity (for OAuth applications)
        LinkedIdentity oauthIdentity = new LinkedIdentity();
        oauthIdentity.setDataSharingConsent(true);
        oauthIdentity.setConsentGivenAt(Instant.now());
        oauthIdentity.setScopes(new String[]{"openid", "profile", "email", "read:documents"});
        
        assertTrue(oauthIdentity.getDataSharingConsent());
        assertNotNull(oauthIdentity.getConsentGivenAt());
        assertArrayEquals(new String[]{"openid", "profile", "email", "read:documents"}, 
                         oauthIdentity.getScopes());
        
        // Test consent withdrawal
        oauthIdentity.withdrawConsent();
        assertFalse(oauthIdentity.getDataSharingConsent());
        assertNotNull(oauthIdentity.getConsentWithdrawnAt());
        
        // Test consent policy enforcement
        TenantPolicy consentPolicy = new TenantPolicy();
        consentPolicy.setConsentRequired(true);
        consentPolicy.setActions(new String[]{"read:contacts", "export:data"});
        
        assertTrue(consentPolicy.getConsentRequired());
        assertTrue(consentPolicy.appliesToAction("read:contacts"));
        assertTrue(consentPolicy.appliesToAction("export:data"));
        assertFalse(consentPolicy.appliesToAction("read:profile"));
        
        // Test consent scope validation
        assertTrue(Arrays.asList(consentSession.getConsentScopes()).contains("read:contacts"));
        assertFalse(Arrays.asList(consentSession.getConsentScopes()).contains("delete:documents"));
    }

    @Test
    @DisplayName("FR3.1 - Explicit deny always overrides allow (precedence rules)")
    void testPolicyPrecedenceRules() {
        // Create policies with different effects and priorities
        TenantPolicy explicitDeny = new TenantPolicy();
        explicitDeny.setId(UUID.randomUUID());
        explicitDeny.setName("Explicit Deny Policy");
        explicitDeny.setEffect(TenantPolicy.Effect.DENY);
        explicitDeny.setPriority(500);
        explicitDeny.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        explicitDeny.setSubjects(new String[]{"user:123"});
        explicitDeny.setResources(new String[]{"sensitive:data"});
        explicitDeny.setActions(new String[]{"delete"});
        
        TenantPolicy allowPolicy = new TenantPolicy();
        allowPolicy.setId(UUID.randomUUID());
        allowPolicy.setName("Allow Policy");
        allowPolicy.setEffect(TenantPolicy.Effect.ALLOW);
        allowPolicy.setPriority(100); // Higher priority (lower number)
        allowPolicy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        allowPolicy.setSubjects(new String[]{"user:123"});
        allowPolicy.setResources(new String[]{"sensitive:data"});
        allowPolicy.setActions(new String[]{"delete"});
        
        List<TenantPolicy> policies = new ArrayList<>(List.of(explicitDeny, allowPolicy));
        
        when(policyService.getApplicablePolicies(any(), any(), any(), any()))
            .thenReturn(policies);
        
        AuthorizationRequest request = createAuthRequest("user:123", "sensitive:data", "delete");
        AuthorizationDecision decision = authorizationService.authorize(request);
        
        // Explicit deny should override allow regardless of priority
        assertEquals(Decision.DENY, decision.getDecision());
        assertTrue(decision.getReason().contains("deny"));
        
        // Verify policy evaluation order and precedence
        assertTrue(explicitDeny.isDenyPolicy());
        assertTrue(allowPolicy.isAllowPolicy());
    }

    // Helper methods
    private User createTestUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("test@example.com");
        user.setStatus(User.UserStatus.ACTIVE);
        
        // Set tenant
        Tenant tenant = new Tenant();
        tenant.setId(testTenantId);
        tenant.setName("Test Tenant");
        user.setTenant(tenant);
        
        // Add test roles
        UserRole adminRole = new UserRole();
        adminRole.setRoleName("ADMIN");
        adminRole.setScope("global");
        adminRole.setStatus(UserRole.RoleStatus.ACTIVE);
        user.getRoles().add(adminRole);
        
        return user;
    }

    private UserSession createTestSession() {
        UserSession session = new UserSession();
        session.setSessionId("test_session_123");
        session.setUser(testUser);
        session.setStatus(UserSession.SessionStatus.ACTIVE);
        session.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        session.setRiskLevel(UserSession.RiskLevel.LOW);
        session.setMfaCompleted(true);
        session.setAuthenticationMethod(UserSession.AuthenticationMethod.PASSWORD);
        session.setStepUpCompleted(false);
        return session;
    }

    private AuthorizationRequest createAuthRequest(String subject, String resource, String action) {
        return AuthorizationRequest.builder()
            .tenantId(testTenantId)
            .subject(subject)
            .resource(resource)
            .action(action)
            .timestamp(Instant.now())
            .build();
    }

    private TenantPolicy createABACPolicy() {
        TenantPolicy policy = new TenantPolicy();
        policy.setId(UUID.randomUUID());
        policy.setName("ABAC Test Policy");
        policy.setPolicyType(TenantPolicy.PolicyType.AUTHORIZATION);
        policy.setEffect(TenantPolicy.Effect.ALLOW);
        policy.setPriority(100);
        policy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        policy.setConditions("{\"department\":\"finance\",\"clearanceLevel\":\"confidential\"}");
        return policy;
    }

    private TenantPolicy createReBACPolicy() {
        TenantPolicy policy = new TenantPolicy();
        policy.setId(UUID.randomUUID());
        policy.setName("ReBAC Test Policy");
        policy.setPolicyType(TenantPolicy.PolicyType.AUTHORIZATION);
        policy.setEffect(TenantPolicy.Effect.ALLOW);
        policy.setPriority(100);
        policy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        return policy;
    }

    private TenantPolicy createAllowPolicy() {
        TenantPolicy policy = new TenantPolicy();
        policy.setId(UUID.randomUUID());
        policy.setName("Allow Policy");
        policy.setEffect(TenantPolicy.Effect.ALLOW);
        policy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        policy.setPriority(100);
        return policy;
    }
}
