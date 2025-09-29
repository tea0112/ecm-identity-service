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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for FR4 - Multi-Tenancy requirements using Testcontainers.
 * These tests validate complete tenant isolation and cross-tenant collaboration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("FR4 - Multi-Tenancy Integration Tests")
class MultiTenancyIntegrationTest {

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantPolicyRepository policyRepository;

    @Autowired
    private UserRoleRepository roleRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private String baseUrl;
    private Tenant tenant1;
    private Tenant tenant2;
    private User user1;
    private User user2;
    private User admin1;
    private User admin2;
    private String user1Token;
    private String user2Token;
    private String admin1Token;
    private String admin2Token;

    @BeforeEach
    @Transactional
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1";
        
        // Clean up test data
        auditEventRepository.deleteAll();
        roleRepository.deleteAll();
        policyRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
        
        // Create tenant 1
        tenant1 = new Tenant();
        tenant1.setTenantCode("acme-corp");
        tenant1.setName("ACME Corporation");
        tenant1.setStatus(Tenant.TenantStatus.ACTIVE);
        tenant1.setMaxUsers(100);
        tenant1.setCryptoKeyId("tenant1_key_123");
        tenant1.setBackupRpoMinutes(5);
        tenant1.setBackupRtoMinutes(60);
        tenant1.setSettings("{\"passwordPolicy\":{\"minLength\":12,\"requireMfa\":true},\"sessionTimeout\":1800}");
        tenant1 = tenantRepository.save(tenant1);
        
        // Create tenant 2
        tenant2 = new Tenant();
        tenant2.setTenantCode("globex-ltd");
        tenant2.setName("Globex Limited");
        tenant2.setStatus(Tenant.TenantStatus.ACTIVE);
        tenant2.setMaxUsers(50);
        tenant2.setCryptoKeyId("tenant2_key_456");
        tenant2.setBackupRpoMinutes(15);
        tenant2.setBackupRtoMinutes(120);
        tenant2.setSettings("{\"passwordPolicy\":{\"minLength\":8,\"requireMfa\":false},\"sessionTimeout\":3600}");
        tenant2 = tenantRepository.save(tenant2);
        
        // Create users for tenant 1
        user1 = new User();
        user1.setTenant(tenant1);
        user1.setEmail("user1@acme-corp.com");
        user1.setUsername("user1");
        user1.setStatus(User.UserStatus.ACTIVE);
        user1 = userRepository.save(user1);
        
        admin1 = new User();
        admin1.setTenant(tenant1);
        admin1.setEmail("admin1@acme-corp.com");
        admin1.setUsername("admin1");
        admin1.setStatus(User.UserStatus.ACTIVE);
        admin1 = userRepository.save(admin1);
        
        // Create users for tenant 2
        user2 = new User();
        user2.setTenant(tenant2);
        user2.setEmail("user2@globex-ltd.com");
        user2.setUsername("user2");
        user2.setStatus(User.UserStatus.ACTIVE);
        user2 = userRepository.save(user2);
        
        admin2 = new User();
        admin2.setTenant(tenant2);
        admin2.setEmail("admin2@globex-ltd.com");
        admin2.setUsername("admin2");
        admin2.setStatus(User.UserStatus.ACTIVE);
        admin2 = userRepository.save(admin2);
        
        // Create admin roles
        UserRole adminRole1 = new UserRole();
        adminRole1.setUser(admin1);
        adminRole1.setRoleName("TENANT_ADMIN");
        adminRole1.setScope("tenant:" + tenant1.getTenantCode());
        adminRole1.setStatus(UserRole.RoleStatus.ACTIVE);
        roleRepository.save(adminRole1);
        
        UserRole adminRole2 = new UserRole();
        adminRole2.setUser(admin2);
        adminRole2.setRoleName("TENANT_ADMIN");
        adminRole2.setScope("tenant:" + tenant2.getTenantCode());
        adminRole2.setStatus(UserRole.RoleStatus.ACTIVE);
        roleRepository.save(adminRole2);
        
        // Get authentication tokens
        user1Token = authenticateUser(user1.getEmail(), "user1_password", tenant1.getTenantCode());
        user2Token = authenticateUser(user2.getEmail(), "user2_password", tenant2.getTenantCode());
        admin1Token = authenticateUser(admin1.getEmail(), "admin1_password", tenant1.getTenantCode());
        admin2Token = authenticateUser(admin2.getEmail(), "admin2_password", tenant2.getTenantCode());
    }

    @Test
    @DisplayName("FR4.1 - Enforce strong data isolation between tenants and per-tenant security policies")
    void testTenantIsolationAndConfiguration() {
        // Test data isolation - users can only see their own tenant's data
        HttpHeaders user1Headers = new HttpHeaders();
        user1Headers.setBearerAuth(user1Token);
        user1Headers.set("X-Tenant-ID", tenant1.getTenantCode());
        HttpEntity<Void> user1Entity = new HttpEntity<>(user1Headers);
        
        ResponseEntity<Map> user1Response = restTemplate.exchange(
            baseUrl + "/users",
            HttpMethod.GET,
            user1Entity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, user1Response.getStatusCode());
        Map<String, Object> user1Result = user1Response.getBody();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> user1List = (List<Map<String, Object>>) user1Result.get("users");
        
        // Should only see users from tenant1
        assertTrue(user1List.stream()
            .allMatch(u -> u.get("email").toString().contains("acme-corp.com")));
        assertFalse(user1List.stream()
            .anyMatch(u -> u.get("email").toString().contains("globex-ltd.com")));
        
        // Test tenant-specific policies
        HttpHeaders admin1Headers = new HttpHeaders();
        admin1Headers.setBearerAuth(admin1Token);
        admin1Headers.set("X-Tenant-ID", tenant1.getTenantCode());
        
        ResponseEntity<Map> tenant1PolicyResponse = restTemplate.exchange(
            baseUrl + "/admin/policies",
            HttpMethod.GET,
            new HttpEntity<>(admin1Headers),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, tenant1PolicyResponse.getStatusCode());
        Map<String, Object> tenant1Policies = tenant1PolicyResponse.getBody();
        
        // Verify tenant-specific settings
        @SuppressWarnings("unchecked")
        Map<String, Object> passwordPolicy = (Map<String, Object>) tenant1Policies.get("passwordPolicy");
        assertEquals(12, passwordPolicy.get("minLength"));
        assertTrue((Boolean) passwordPolicy.get("requireMfa"));
        
        // Test tenant-specific cryptographic keys
        ResponseEntity<Map> cryptoConfigResponse = restTemplate.exchange(
            baseUrl + "/admin/crypto/config",
            HttpMethod.GET,
            new HttpEntity<>(admin1Headers),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, cryptoConfigResponse.getStatusCode());
        Map<String, Object> cryptoConfig = cryptoConfigResponse.getBody();
        assertEquals("tenant1_key_123", cryptoConfig.get("encryptionKeyId"));
        assertNotEquals("tenant2_key_456", cryptoConfig.get("encryptionKeyId"));
        
        // Test tenant-specific backup/recovery settings
        ResponseEntity<Map> backupConfigResponse = restTemplate.exchange(
            baseUrl + "/admin/backup/config",
            HttpMethod.GET,
            new HttpEntity<>(admin1Headers),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, backupConfigResponse.getStatusCode());
        Map<String, Object> backupConfig = backupConfigResponse.getBody();
        assertEquals(5, backupConfig.get("rpoMinutes"));
        assertEquals(60, backupConfig.get("rtoMinutes"));
    }

    @Test
    @DisplayName("FR4.2 - Support complex operations like splitting and merging organizations")
    void testTenantLifecycleManagement() {
        // Test tenant splitting scenario
        Map<String, Object> splitRequest = Map.of(
            "sourceTenantId", tenant1.getId().toString(),
            "newTenantCode", "acme-division",
            "newTenantName", "ACME Division",
            "usersToMigrate", List.of(user1.getId().toString()),
            "reason", "Organizational restructuring - spinning off division"
        );
        
        HttpHeaders admin1Headers = new HttpHeaders();
        admin1Headers.setBearerAuth(admin1Token);
        admin1Headers.set("X-Tenant-ID", tenant1.getTenantCode());
        HttpEntity<Map<String, Object>> splitEntity = new HttpEntity<>(splitRequest, admin1Headers);
        
        ResponseEntity<Map> splitResponse = restTemplate.exchange(
            baseUrl + "/admin/tenants/split",
            HttpMethod.POST,
            splitEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, splitResponse.getStatusCode());
        Map<String, Object> splitResult = splitResponse.getBody();
        assertEquals("SPLIT_INITIATED", splitResult.get("status"));
        String splitOperationId = (String) splitResult.get("operationId");
        assertNotNull(splitOperationId);
        
        // Test tenant merging scenario
        Map<String, Object> mergeRequest = Map.of(
            "sourceTenantId", tenant2.getId().toString(),
            "targetTenantId", tenant1.getId().toString(),
            "usersToMigrate", List.of(user2.getId().toString()),
            "reason", "Company acquisition - merging organizations",
            "preserveRoles", true,
            "roleMapping", Map.of(
                "USER", "EXTERNAL_USER",
                "ADMIN", "EXTERNAL_ADMIN"
            )
        );
        
        HttpEntity<Map<String, Object>> mergeEntity = new HttpEntity<>(mergeRequest, admin1Headers);
        
        ResponseEntity<Map> mergeResponse = restTemplate.exchange(
            baseUrl + "/admin/tenants/merge",
            HttpMethod.POST,
            mergeEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, mergeResponse.getStatusCode());
        Map<String, Object> mergeResult = mergeResponse.getBody();
        assertEquals("MERGE_INITIATED", mergeResult.get("status"));
        String mergeOperationId = (String) mergeResult.get("operationId");
        assertNotNull(mergeOperationId);
        
        // Test operation status tracking
        ResponseEntity<Map> statusResponse = restTemplate.exchange(
            baseUrl + "/admin/tenants/operations/" + mergeOperationId + "/status",
            HttpMethod.GET,
            new HttpEntity<>(admin1Headers),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        Map<String, Object> statusResult = statusResponse.getBody();
        assertTrue(List.of("PROCESSING", "COMPLETED", "FAILED").contains(statusResult.get("status")));
        
        // Verify audit trail for tenant operations
        var auditEvents = auditEventRepository.findByTenantId(tenant1.getId());
        boolean hasSplitEvent = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals("tenant.split.initiated"));
        boolean hasMergeEvent = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals("tenant.merge.initiated"));
        
        assertTrue(hasSplitEvent || hasMergeEvent);
    }

    @Test
    @DisplayName("FR4.3 - Securely support guest users, sharing, and third-party application marketplaces")
    void testCrossTenantCollaboration() {
        // Create guest user access from tenant1 to tenant2
        Map<String, Object> guestAccessRequest = Map.of(
            "guestUserId", user1.getId().toString(),
            "hostTenantId", tenant2.getId().toString(),
            "permissions", List.of("read", "comment"),
            "scope", "project:shared-initiative",
            "duration", "P7D", // 7 days ISO 8601 duration
            "justification", "Cross-organizational collaboration on shared project",
            "approverRequired", true
        );
        
        HttpHeaders admin2Headers = new HttpHeaders();
        admin2Headers.setBearerAuth(admin2Token);
        admin2Headers.set("X-Tenant-ID", tenant2.getTenantCode());
        HttpEntity<Map<String, Object>> guestEntity = new HttpEntity<>(guestAccessRequest, admin2Headers);
        
        ResponseEntity<Map> guestResponse = restTemplate.exchange(
            baseUrl + "/admin/cross-tenant/guest-access",
            HttpMethod.POST,
            guestEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, guestResponse.getStatusCode());
        Map<String, Object> guestResult = guestResponse.getBody();
        assertEquals("GUEST_ACCESS_PENDING", guestResult.get("status"));
        String guestAccessId = (String) guestResult.get("guestAccessId");
        assertNotNull(guestAccessId);
        
        // Test guest user authentication with cross-tenant access
        Map<String, Object> guestLoginRequest = Map.of(
            "email", user1.getEmail(),
            "password", "user1_password",
            "targetTenant", tenant2.getTenantCode(),
            "guestAccessId", guestAccessId
        );
        
        ResponseEntity<Map> guestLoginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/guest-login",
            guestLoginRequest,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, guestLoginResponse.getStatusCode());
        Map<String, Object> guestLoginResult = guestLoginResponse.getBody();
        String guestToken = (String) guestLoginResult.get("accessToken");
        assertNotNull(guestToken);
        assertEquals("GUEST", guestLoginResult.get("userType"));
        assertEquals(tenant2.getTenantCode(), guestLoginResult.get("activeTenant"));
        
        // Test guest user permissions
        HttpHeaders guestHeaders = new HttpHeaders();
        guestHeaders.setBearerAuth(guestToken);
        guestHeaders.set("X-Tenant-ID", tenant2.getTenantCode());
        
        Map<String, Object> guestAuthzRequest = Map.of(
            "subject", "user:" + user1.getId(),
            "resource", "project:shared-initiative:documents",
            "action", "read",
            "context", Map.of(
                "crossTenant", true,
                "guestAccess", true
            )
        );
        
        HttpEntity<Map<String, Object>> guestAuthzEntity = new HttpEntity<>(guestAuthzRequest, guestHeaders);
        
        ResponseEntity<Map> guestAuthzResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            guestAuthzEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, guestAuthzResponse.getStatusCode());
        Map<String, Object> guestAuthzResult = guestAuthzResponse.getBody();
        assertEquals("ALLOW", guestAuthzResult.get("decision"));
        assertTrue(guestAuthzResult.get("reason").toString().contains("guest access"));
        
        // Test marketplace application integration
        Map<String, Object> marketplaceAppRequest = Map.of(
            "appId", "marketplace:crm-connector",
            "tenantId", tenant1.getId().toString(),
            "requestedScopes", List.of("read:users", "read:profiles", "write:activities"),
            "dataAccess", Map.of(
                "users", List.of("email", "name", "department"),
                "activities", List.of("login_history", "session_data")
            ),
            "purpose", "CRM synchronization and activity tracking"
        );
        
        HttpEntity<Map<String, Object>> marketplaceEntity = new HttpEntity<>(marketplaceAppRequest, admin1Headers);
        
        ResponseEntity<Map> marketplaceResponse = restTemplate.exchange(
            baseUrl + "/admin/marketplace/install",
            HttpMethod.POST,
            marketplaceEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, marketplaceResponse.getStatusCode());
        Map<String, Object> marketplaceResult = marketplaceResponse.getBody();
        assertEquals("APP_INSTALL_PENDING", marketplaceResult.get("status"));
        assertTrue((Boolean) marketplaceResult.get("consentRequired"));
        
        // Test user consent for marketplace app
        Map<String, Object> appConsentRequest = Map.of(
            "appId", "marketplace:crm-connector",
            "consentGiven", true,
            "scopes", List.of("read:users", "read:profiles"),
            "dataFields", List.of("email", "name"),
            "purpose", "CRM integration"
        );
        
        HttpHeaders user1Headers = new HttpHeaders();
        user1Headers.setBearerAuth(user1Token);
        user1Headers.set("X-Tenant-ID", tenant1.getTenantCode());
        HttpEntity<Map<String, Object>> consentEntity = new HttpEntity<>(appConsentRequest, user1Headers);
        
        ResponseEntity<Map> consentResponse = restTemplate.exchange(
            baseUrl + "/privacy/app-consent",
            HttpMethod.POST,
            consentEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, consentResponse.getStatusCode());
        Map<String, Object> consentResult = consentResponse.getBody();
        assertEquals("CONSENT_GRANTED", consentResult.get("status"));
        
        // Test cross-tenant sharing expiration
        Map<String, Object> sharingExpirationRequest = Map.of(
            "guestAccessId", guestAccessId,
            "checkExpiration", true
        );
        
        HttpEntity<Map<String, Object>> expirationEntity = new HttpEntity<>(sharingExpirationRequest, admin2Headers);
        
        ResponseEntity<Map> expirationResponse = restTemplate.exchange(
            baseUrl + "/admin/cross-tenant/expiration-check",
            HttpMethod.POST,
            expirationEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, expirationResponse.getStatusCode());
        Map<String, Object> expirationResult = expirationResponse.getBody();
        assertNotNull(expirationResult.get("expiresAt"));
        assertTrue((Boolean) expirationResult.get("isActive"));
    }

    @Test
    @DisplayName("FR4.1 - Tenant suspension and reactivation with data isolation")
    void testTenantStatusManagement() {
        // Test tenant suspension
        Map<String, Object> suspensionRequest = Map.of(
            "tenantId", tenant2.getId().toString(),
            "reason", "Payment overdue - account suspended",
            "suspensionType", "BILLING",
            "notifyUsers", true
        );
        
        HttpHeaders systemAdminHeaders = new HttpHeaders();
        systemAdminHeaders.setBearerAuth(admin1Token); // Using admin1 as system admin
        HttpEntity<Map<String, Object>> suspensionEntity = new HttpEntity<>(suspensionRequest, systemAdminHeaders);
        
        ResponseEntity<Map> suspensionResponse = restTemplate.exchange(
            baseUrl + "/admin/system/tenants/suspend",
            HttpMethod.POST,
            suspensionEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, suspensionResponse.getStatusCode());
        Map<String, Object> suspensionResult = suspensionResponse.getBody();
        assertEquals("TENANT_SUSPENDED", suspensionResult.get("status"));
        
        // Test access during suspension
        HttpHeaders user2Headers = new HttpHeaders();
        user2Headers.setBearerAuth(user2Token);
        user2Headers.set("X-Tenant-ID", tenant2.getTenantCode());
        
        ResponseEntity<Map> suspendedAccessResponse = restTemplate.exchange(
            baseUrl + "/users/me",
            HttpMethod.GET,
            new HttpEntity<>(user2Headers),
            Map.class
        );
        
        assertEquals(HttpStatus.FORBIDDEN, suspendedAccessResponse.getStatusCode());
        Map<String, Object> suspendedError = suspendedAccessResponse.getBody();
        assertEquals("TENANT_SUSPENDED", suspendedError.get("errorCode"));
        
        // Test tenant reactivation
        Map<String, Object> reactivationRequest = Map.of(
            "tenantId", tenant2.getId().toString(),
            "reason", "Payment received - reactivating account",
            "notifyUsers", true
        );
        
        HttpEntity<Map<String, Object>> reactivationEntity = new HttpEntity<>(reactivationRequest, systemAdminHeaders);
        
        ResponseEntity<Map> reactivationResponse = restTemplate.exchange(
            baseUrl + "/admin/system/tenants/reactivate",
            HttpMethod.POST,
            reactivationEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, reactivationResponse.getStatusCode());
        Map<String, Object> reactivationResult = reactivationResponse.getBody();
        assertEquals("TENANT_REACTIVATED", reactivationResult.get("status"));
        
        // Test access after reactivation
        ResponseEntity<Map> reactivatedAccessResponse = restTemplate.exchange(
            baseUrl + "/users/me",
            HttpMethod.GET,
            new HttpEntity<>(user2Headers),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, reactivatedAccessResponse.getStatusCode());
        assertNotNull(reactivatedAccessResponse.getBody().get("email"));
    }

    @Test
    @DisplayName("Complete multi-tenant workflow: Cross-tenant collaboration with audit trail")
    void testCompleteCrossTenantCollaborationWorkflow() {
        // Step 1: Create shared project policy in tenant2
        TenantPolicy sharedPolicy = new TenantPolicy();
        sharedPolicy.setTenant(tenant2);
        sharedPolicy.setName("Cross-Tenant Collaboration Policy");
        sharedPolicy.setPolicyType(TenantPolicy.PolicyType.AUTHORIZATION);
        sharedPolicy.setEffect(TenantPolicy.Effect.ALLOW);
        sharedPolicy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        sharedPolicy.setSubjects(new String[]{"external:*"});
        sharedPolicy.setResources(new String[]{"project:collaboration:*"});
        sharedPolicy.setActions(new String[]{"read", "comment", "contribute"});
        sharedPolicy.setConsentRequired(true);
        policyRepository.save(sharedPolicy);
        
        // Step 2: Request cross-tenant access
        Map<String, Object> collaborationRequest = Map.of(
            "fromTenant", tenant1.getTenantCode(),
            "toTenant", tenant2.getTenantCode(),
            "userId", user1.getId().toString(),
            "projectId", "collaboration:shared-project",
            "permissions", List.of("read", "comment"),
            "businessJustification", "Joint venture project collaboration",
            "duration", "P30D", // 30 days
            "dataAccess", List.of("project_documents", "comments", "activities")
        );
        
        HttpHeaders admin1Headers = new HttpHeaders();
        admin1Headers.setBearerAuth(admin1Token);
        admin1Headers.set("X-Tenant-ID", tenant1.getTenantCode());
        HttpEntity<Map<String, Object>> collabEntity = new HttpEntity<>(collaborationRequest, admin1Headers);
        
        ResponseEntity<Map> collabResponse = restTemplate.exchange(
            baseUrl + "/admin/cross-tenant/collaboration/request",
            HttpMethod.POST,
            collabEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, collabResponse.getStatusCode());
        Map<String, Object> collabResult = collabResponse.getBody();
        String collaborationId = (String) collabResult.get("collaborationId");
        assertEquals("COLLABORATION_PENDING", collabResult.get("status"));
        
        // Step 3: Approve collaboration request
        Map<String, Object> approvalRequest = Map.of(
            "collaborationId", collaborationId,
            "approved", true,
            "approverComment", "Approved for joint project work",
            "additionalRestrictions", Map.of(
                "timeRestriction", "business_hours_only",
                "ipRestriction", "office_networks_only"
            )
        );
        
        HttpHeaders admin2Headers = new HttpHeaders();
        admin2Headers.setBearerAuth(admin2Token);
        admin2Headers.set("X-Tenant-ID", tenant2.getTenantCode());
        HttpEntity<Map<String, Object>> approvalEntity = new HttpEntity<>(approvalRequest, admin2Headers);
        
        ResponseEntity<Map> approvalResponse = restTemplate.exchange(
            baseUrl + "/admin/cross-tenant/collaboration/approve",
            HttpMethod.POST,
            approvalEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, approvalResponse.getStatusCode());
        Map<String, Object> approvalResult = approvalResponse.getBody();
        assertEquals("COLLABORATION_APPROVED", approvalResult.get("status"));
        
        // Step 4: User provides explicit consent
        Map<String, Object> userConsentRequest = Map.of(
            "collaborationId", collaborationId,
            "consentGiven", true,
            "acknowledgedDataSharing", true,
            "acknowledgedCrossTenantAccess", true
        );
        
        HttpHeaders user1Headers = new HttpHeaders();
        user1Headers.setBearerAuth(user1Token);
        user1Headers.set("X-Tenant-ID", tenant1.getTenantCode());
        HttpEntity<Map<String, Object>> userConsentEntity = new HttpEntity<>(userConsentRequest, user1Headers);
        
        ResponseEntity<Map> userConsentResponse = restTemplate.exchange(
            baseUrl + "/privacy/cross-tenant-consent",
            HttpMethod.POST,
            userConsentEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, userConsentResponse.getStatusCode());
        Map<String, Object> userConsentResult = userConsentResponse.getBody();
        assertEquals("CONSENT_GRANTED", userConsentResult.get("status"));
        
        // Step 5: Access shared resources with cross-tenant token
        Map<String, Object> crossTenantLoginRequest = Map.of(
            "email", user1.getEmail(),
            "password", "user1_password",
            "targetTenant", tenant2.getTenantCode(),
            "collaborationId", collaborationId
        );
        
        ResponseEntity<Map> crossTenantLoginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/cross-tenant-login",
            crossTenantLoginRequest,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, crossTenantLoginResponse.getStatusCode());
        Map<String, Object> crossTenantResult = crossTenantLoginResponse.getBody();
        String crossTenantToken = (String) crossTenantResult.get("accessToken");
        assertNotNull(crossTenantToken);
        
        // Step 6: Test cross-tenant resource access
        HttpHeaders crossTenantHeaders = new HttpHeaders();
        crossTenantHeaders.setBearerAuth(crossTenantToken);
        crossTenantHeaders.set("X-Tenant-ID", tenant2.getTenantCode());
        
        Map<String, Object> resourceAccessRequest = Map.of(
            "subject", "external:" + user1.getId(),
            "resource", "project:collaboration:shared-project:documents",
            "action", "read",
            "context", Map.of(
                "crossTenant", true,
                "collaborationId", collaborationId
            )
        );
        
        HttpEntity<Map<String, Object>> resourceEntity = new HttpEntity<>(resourceAccessRequest, crossTenantHeaders);
        
        ResponseEntity<Map> resourceResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            resourceEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, resourceResponse.getStatusCode());
        Map<String, Object> resourceResult = resourceResponse.getBody();
        assertEquals("ALLOW", resourceResult.get("decision"));
        
        // Step 7: Verify complete audit trail
        var tenant1Audits = auditEventRepository.findByTenantId(tenant1.getId());
        var tenant2Audits = auditEventRepository.findByTenantId(tenant2.getId());
        
        boolean hasRequestEvent = tenant1Audits.stream()
            .anyMatch(e -> e.getEventType().equals("cross_tenant.collaboration.requested"));
        boolean hasApprovalEvent = tenant2Audits.stream()
            .anyMatch(e -> e.getEventType().equals("cross_tenant.collaboration.approved"));
        boolean hasAccessEvent = tenant2Audits.stream()
            .anyMatch(e -> e.getEventType().equals("cross_tenant.resource.accessed"));
        
        assertTrue(hasRequestEvent || hasApprovalEvent || hasAccessEvent);
    }

    private String authenticateUser(String email, String password, String tenantCode) {
        Map<String, Object> loginRequest = Map.of(
            "email", email,
            "password", password,
            "tenantCode", tenantCode
        );
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Tenant-ID", tenantCode);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(loginRequest, headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(
            baseUrl + "/auth/login",
            HttpMethod.POST,
            entity,
            Map.class
        );
        
        if (response.getStatusCode() == HttpStatus.OK) {
            return (String) response.getBody().get("accessToken");
        }
        return null;
    }
}
