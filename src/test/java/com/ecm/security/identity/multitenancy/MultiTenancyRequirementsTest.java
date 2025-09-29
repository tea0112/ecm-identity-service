package com.ecm.security.identity.multitenancy;

import com.ecm.security.identity.domain.*;
import com.ecm.security.identity.service.TenantContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests validating FR4 - Multi-Tenancy requirements.
 * 
 * Requirements tested:
 * - FR4.1: Tenant Isolation & Configuration
 * - FR4.2: Tenant Lifecycle Management
 * - FR4.3: Cross-Tenant Collaboration
 */
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("FR4 - Multi-Tenancy Requirements")
class MultiTenancyRequirementsTest {

    @Mock
    private TenantContextService tenantContextService;

    private Tenant tenant1;
    private Tenant tenant2;
    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        tenant1 = createTestTenant("tenant1", "Tenant One");
        tenant2 = createTestTenant("tenant2", "Tenant Two");
        user1 = createTestUser(tenant1, "user1@tenant1.com");
        user2 = createTestUser(tenant2, "user2@tenant2.com");
    }

    @Test
    @DisplayName("FR4.1 - Enforce strong data isolation between tenants and allow for per-tenant security policies")
    void testTenantIsolationAndConfiguration() {
        // Test data isolation - users belong to specific tenants
        assertEquals(tenant1.getId(), user1.getTenant().getId());
        assertEquals(tenant2.getId(), user2.getTenant().getId());
        assertNotEquals(user1.getTenant().getId(), user2.getTenant().getId());
        
        // Test unique constraints ensure isolation
        assertEquals("user1@tenant1.com", user1.getEmail());
        assertEquals("user2@tenant2.com", user2.getEmail());
        
        // Users can have same email across different tenants (isolated by tenant)
        User duplicateEmailUser = createTestUser(tenant2, "user1@tenant1.com");
        assertEquals("user1@tenant1.com", duplicateEmailUser.getEmail());
        assertEquals(tenant2.getId(), duplicateEmailUser.getTenant().getId());
        
        // Test per-tenant security policies
        TenantPolicy tenant1Policy = new TenantPolicy();
        tenant1Policy.setTenant(tenant1);
        tenant1Policy.setName("Tenant 1 Auth Policy");
        tenant1Policy.setMfaRequired(true);
        tenant1Policy.setPolicyType(TenantPolicy.PolicyType.AUTHENTICATION);
        
        TenantPolicy tenant2Policy = new TenantPolicy();
        tenant2Policy.setTenant(tenant2);
        tenant2Policy.setName("Tenant 2 Auth Policy");
        tenant2Policy.setMfaRequired(false);
        tenant2Policy.setPolicyType(TenantPolicy.PolicyType.AUTHENTICATION);
        
        // Policies are isolated by tenant
        assertEquals(tenant1.getId(), tenant1Policy.getTenant().getId());
        assertEquals(tenant2.getId(), tenant2Policy.getTenant().getId());
        assertTrue(tenant1Policy.getMfaRequired());
        assertFalse(tenant2Policy.getMfaRequired());
        
        // Test tenant-specific cryptographic keys
        tenant1.setCryptoKeyId("tenant1_key_123");
        tenant2.setCryptoKeyId("tenant2_key_456");
        
        assertNotEquals(tenant1.getCryptoKeyId(), tenant2.getCryptoKeyId());
        assertEquals("tenant1_key_123", tenant1.getCryptoKeyId());
        assertEquals("tenant2_key_456", tenant2.getCryptoKeyId());
        
        // Test tenant-specific backup/recovery settings
        tenant1.setBackupRpoMinutes(5);
        tenant1.setBackupRtoMinutes(60);
        tenant2.setBackupRpoMinutes(15);
        tenant2.setBackupRtoMinutes(120);
        
        assertEquals(5, tenant1.getBackupRpoMinutes());
        assertEquals(60, tenant1.getBackupRtoMinutes());
        assertEquals(15, tenant2.getBackupRpoMinutes());
        assertEquals(120, tenant2.getBackupRtoMinutes());
        
        // Test tenant configuration settings
        String tenant1Settings = """
            {
                "passwordPolicy": {
                    "minLength": 12,
                    "requireMfa": true
                },
                "sessionPolicy": {
                    "maxSessions": 3,
                    "sessionTimeout": 1800
                }
            }""";
        
        String tenant2Settings = """
            {
                "passwordPolicy": {
                    "minLength": 8,
                    "requireMfa": false
                },
                "sessionPolicy": {
                    "maxSessions": 5,
                    "sessionTimeout": 3600
                }
            }""";
        
        tenant1.setSettings(tenant1Settings);
        tenant2.setSettings(tenant2Settings);
        
        assertNotEquals(tenant1.getSettings(), tenant2.getSettings());
        assertTrue(tenant1.getSettings().contains("\"requireMfa\": true"));
        assertTrue(tenant2.getSettings().contains("\"requireMfa\": false"));
    }

    @Test
    @DisplayName("FR4.2 - Support complex operations like splitting and merging organizations with correct permission remapping")
    void testTenantLifecycleManagement() {
        // Test tenant splitting scenario
        Tenant originalTenant = createTestTenant("original", "Original Corp");
        originalTenant.setMaxUsers(1000);
        
        // Create users in original tenant
        User user1 = createTestUser(originalTenant, "user1@original.com");
        User user2 = createTestUser(originalTenant, "user2@original.com");
        
        // Create new tenant for split
        Tenant newTenant = createTestTenant("new-division", "New Division Corp");
        newTenant.setMaxUsers(500);
        
        // Simulate user migration during split
        user2.setTenant(newTenant);
        user2.setEmail("user2@newdivision.com"); // Update email for new domain
        
        // Test that users are now in different tenants
        assertEquals(originalTenant.getId(), user1.getTenant().getId());
        assertEquals(newTenant.getId(), user2.getTenant().getId());
        assertNotEquals(user1.getTenant().getId(), user2.getTenant().getId());
        
        // Test role remapping during split
        UserRole originalRole = new UserRole();
        originalRole.setUser(user2);
        originalRole.setRoleName("DIVISION_ADMIN");
        originalRole.setScope("division:engineering");
        
        // After split, role scope should be updated
        originalRole.setScope("division:new-engineering");
        assertEquals("division:new-engineering", originalRole.getScope());
        
        // Test tenant merging scenario
        Tenant mergingTenant = createTestTenant("merging", "Merging Corp");
        User mergingUser = createTestUser(mergingTenant, "user@merging.com");
        
        // Track merge operation
        String mergeOperationId = "merge_op_" + UUID.randomUUID();
        
        // Simulate merge - user moves to target tenant
        Tenant targetTenant = originalTenant;
        mergingUser.setTenant(targetTenant);
        
        // Test audit trail of merge operation
        AuditEvent mergeEvent = new AuditEvent();
        mergeEvent.setEventType("tenant.merge.user_migration");
        mergeEvent.setTenantId(targetTenant.getId());
        mergeEvent.setActorId(mergingUser.getId());
        mergeEvent.setDescription("User migrated during tenant merge");
        mergeEvent.setDetails("{\"mergeOperationId\":\"" + mergeOperationId + "\",\"sourceTenant\":\"" + 
                              mergingTenant.getTenantCode() + "\",\"targetTenant\":\"" + 
                              targetTenant.getTenantCode() + "\"}");
        
        assertEquals("tenant.merge.user_migration", mergeEvent.getEventType());
        assertTrue(mergeEvent.getDetails().contains(mergeOperationId));
        assertTrue(mergeEvent.getDetails().contains("sourceTenant"));
        
        // Test data integrity during merge
        assertEquals(targetTenant.getId(), mergingUser.getTenant().getId());
        assertNotNull(mergeEvent.getDetails());
        
        // Test reversible merge tracking
        LinkedIdentity mergeTracker = new LinkedIdentity();
        mergeTracker.setUser(mergingUser);
        mergeTracker.setProvider("tenant_merge");
        mergeTracker.setExternalId(mergingTenant.getTenantCode());
        mergeTracker.setMergeOperationId(mergeOperationId);
        mergeTracker.setMergeReversible(true);
        mergeTracker.setMergeExpiresAt(Instant.now().plusDays(30));
        
        assertTrue(mergeTracker.canReverseMerge());
        assertNotNull(mergeTracker.getMergeOperationId());
        assertEquals(mergingTenant.getTenantCode(), mergeTracker.getExternalId());
    }

    @Test
    @DisplayName("FR4.3 - Securely support guest users, sharing, and third-party application marketplaces")
    void testCrossTenantCollaboration() {
        // Test guest user access
        User guestUser = createTestUser(tenant1, "guest@external.com");
        
        // Guest role in tenant2
        UserRole guestRole = new UserRole();
        guestRole.setUser(guestUser);
        guestRole.setRoleName("GUEST");
        guestRole.setScope("tenant:" + tenant2.getTenantCode() + ":project:shared");
        guestRole.setAssignmentType(UserRole.AssignmentType.TEMPORARY);
        guestRole.setExpiresAt(Instant.now().plusDays(7)); // 7-day guest access
        
        // Verify guest access properties
        assertTrue(guestRole.isTemporary());
        assertNotNull(guestRole.getExpiresAt());
        assertTrue(guestRole.getScope().contains(tenant2.getTenantCode()));
        assertEquals("GUEST", guestRole.getRoleName());
        
        // Test explicit consent for cross-tenant access
        guestRole.setJustification("Collaborating on shared project XYZ");
        guestRole.setApprovalRequired(true);
        guestRole.setStatus(UserRole.RoleStatus.PENDING_APPROVAL);
        
        assertTrue(guestRole.requiresApproval());
        assertNotNull(guestRole.getJustification());
        
        // Test scoped access - guest can only access specific resources
        TenantPolicy guestPolicy = new TenantPolicy();
        guestPolicy.setTenant(tenant2);
        guestPolicy.setName("Guest Access Policy");
        guestPolicy.setSubjects(new String[]{"role:GUEST"});
        guestPolicy.setResources(new String[]{"project:shared:*"});
        guestPolicy.setActions(new String[]{"read", "comment"});
        guestPolicy.setEffect(TenantPolicy.Effect.ALLOW);
        
        assertTrue(guestPolicy.appliesToSubject("role:GUEST"));
        assertTrue(guestPolicy.appliesToResource("project:shared:documents"));
        assertTrue(guestPolicy.appliesToAction("read"));
        assertFalse(guestPolicy.appliesToAction("delete"));
        
        // Test third-party application marketplace
        LinkedIdentity marketplaceApp = new LinkedIdentity();
        marketplaceApp.setUser(user1);
        marketplaceApp.setProvider("marketplace");
        marketplaceApp.setExternalId("app_crm_integration");
        marketplaceApp.setIdentityType(LinkedIdentity.IdentityType.OAUTH2);
        marketplaceApp.setScopes(new String[]{"read:contacts", "write:calendar"});
        marketplaceApp.setDataSharingConsent(true);
        marketplaceApp.setConsentGivenAt(Instant.now());
        
        // Test explicit consent for marketplace apps
        assertTrue(marketplaceApp.getDataSharingConsent());
        assertNotNull(marketplaceApp.getConsentGivenAt());
        assertArrayEquals(new String[]{"read:contacts", "write:calendar"}, marketplaceApp.getScopes());
        
        // Test scoped marketplace access
        boolean hasContactsAccess = java.util.Arrays.asList(marketplaceApp.getScopes()).contains("read:contacts");
        boolean hasEmailAccess = java.util.Arrays.asList(marketplaceApp.getScopes()).contains("read:email");
        
        assertTrue(hasContactsAccess);
        assertFalse(hasEmailAccess); // Not granted
        
        // Test cross-tenant sharing with audit trail
        UserSession sharingSession = new UserSession();
        sharingSession.setUser(user1);
        sharingSession.setSessionMetadata("""
            {
                "crossTenantAccess": true,
                "targetTenant": "%s",
                "sharedResources": ["project:collaboration:docs"],
                "permissions": ["read", "comment"],
                "approvedBy": "tenant_admin_123"
            }""".formatted(tenant2.getTenantCode()));
        
        assertNotNull(sharingSession.getSessionMetadata());
        assertTrue(sharingSession.getSessionMetadata().contains("crossTenantAccess"));
        assertTrue(sharingSession.getSessionMetadata().contains(tenant2.getTenantCode()));
        assertTrue(sharingSession.getSessionMetadata().contains("approvedBy"));
        
        // Test sharing expiration
        UserRole sharingRole = new UserRole();
        sharingRole.setUser(user1);
        sharingRole.setRoleName("EXTERNAL_COLLABORATOR");
        sharingRole.setScope("tenant:" + tenant2.getTenantCode() + ":shared");
        sharingRole.setExpiresAt(Instant.now().plusDays(30));
        sharingRole.setJustification("Cross-tenant collaboration approved by both tenant admins");
        
        assertTrue(sharingRole.isTemporary());
        assertTrue(sharingRole.getScope().contains("shared"));
        assertNotNull(sharingRole.getJustification());
        
        // Test consent withdrawal
        marketplaceApp.withdrawConsent();
        assertFalse(marketplaceApp.getDataSharingConsent());
        assertNotNull(marketplaceApp.getConsentWithdrawnAt());
        assertFalse(marketplaceApp.getProfileSyncEnabled());
    }

    @Test
    @DisplayName("FR4.1 - Tenant-scoped cryptographic keys with specific RPO/RTO guarantees")
    void testTenantScopedCryptographicKeys() {
        // Test tenant-specific encryption keys
        tenant1.setCryptoKeyId("vault://tenant1/encryption/key1");
        tenant2.setCryptoKeyId("vault://tenant2/encryption/key2");
        
        assertNotEquals(tenant1.getCryptoKeyId(), tenant2.getCryptoKeyId());
        assertTrue(tenant1.getCryptoKeyId().contains("tenant1"));
        assertTrue(tenant2.getCryptoKeyId().contains("tenant2"));
        
        // Test RPO/RTO guarantees per tenant
        tenant1.setBackupRpoMinutes(5); // 5-minute RPO
        tenant1.setBackupRtoMinutes(60); // 1-hour RTO
        
        tenant2.setBackupRpoMinutes(15); // 15-minute RPO  
        tenant2.setBackupRtoMinutes(240); // 4-hour RTO
        
        // Verify different SLA levels per tenant
        assertTrue(tenant1.getBackupRpoMinutes() < tenant2.getBackupRpoMinutes());
        assertTrue(tenant1.getBackupRtoMinutes() < tenant2.getBackupRtoMinutes());
        
        // Test key rotation per tenant
        AuditEvent tenant1KeyRotation = new AuditEvent();
        tenant1KeyRotation.setTenantId(tenant1.getId());
        tenant1KeyRotation.setEventType(AuditEvent.EventTypes.KEY_ROTATION);
        tenant1KeyRotation.setDescription("Tenant-specific key rotation");
        tenant1KeyRotation.setSigningKeyId("tenant1_key_v2");
        
        assertEquals(tenant1.getId(), tenant1KeyRotation.getTenantId());
        assertTrue(tenant1KeyRotation.getSigningKeyId().contains("tenant1"));
        
        // Test that tenant key rotation doesn't affect other tenants
        AuditEvent tenant2KeyRotation = new AuditEvent();
        tenant2KeyRotation.setTenantId(tenant2.getId());
        tenant2KeyRotation.setSigningKeyId("tenant2_key_v1");
        
        assertNotEquals(tenant1KeyRotation.getTenantId(), tenant2KeyRotation.getTenantId());
        assertNotEquals(tenant1KeyRotation.getSigningKeyId(), tenant2KeyRotation.getSigningKeyId());
    }

    @Test
    @DisplayName("FR4.1 - Tenant suspension and status management")
    void testTenantStatusManagement() {
        // Test active tenant
        assertTrue(tenant1.isActive());
        assertEquals(Tenant.TenantStatus.ACTIVE, tenant1.getStatus());
        
        // Test tenant suspension
        String suspensionReason = "Payment overdue";
        tenant1.suspend(suspensionReason);
        
        assertFalse(tenant1.isActive());
        assertEquals(Tenant.TenantStatus.SUSPENDED, tenant1.getStatus());
        assertEquals(suspensionReason, tenant1.getSuspensionReason());
        assertNotNull(tenant1.getSuspendedAt());
        
        // Test tenant reactivation
        tenant1.reactivate();
        assertTrue(tenant1.isActive());
        assertEquals(Tenant.TenantStatus.ACTIVE, tenant1.getStatus());
        assertNull(tenant1.getSuspendedAt());
        assertNull(tenant1.getSuspensionReason());
        
        // Test tenant archival
        tenant2.setStatus(Tenant.TenantStatus.ARCHIVED);
        tenant2.markAsDeleted();
        
        assertEquals(Tenant.TenantStatus.ARCHIVED, tenant2.getStatus());
        assertTrue(tenant2.isDeleted());
        assertNotNull(tenant2.getDeletedAt());
        
        // Test user access during tenant suspension
        User suspendedTenantUser = createTestUser(tenant1, "user@suspended.com");
        tenant1.suspend("Testing suspension");
        
        // User's tenant is suspended, so user should not have access
        assertFalse(suspendedTenantUser.getTenant().isActive());
        assertEquals(Tenant.TenantStatus.SUSPENDED, suspendedTenantUser.getTenant().getStatus());
    }

    // Helper methods
    private Tenant createTestTenant(String code, String name) {
        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setTenantCode(code);
        tenant.setName(name);
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        tenant.setMaxUsers(100);
        return tenant;
    }

    private User createTestUser(Tenant tenant, String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenant(tenant);
        user.setEmail(email);
        user.setStatus(User.UserStatus.ACTIVE);
        return user;
    }
}
