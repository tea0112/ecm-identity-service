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
 * Integration tests for FR3 - Authorization & Access Control requirements using Testcontainers.
 * These tests validate the complete authorization flow with real database and Redis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("FR3 - Authorization & Access Control Integration Tests")
class AuthorizationIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantPolicyRepository policyRepository;

    @Autowired
    private UserRoleRepository roleRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private String baseUrl;
    private Tenant testTenant;
    private User adminUser;
    private User regularUser;
    private String adminToken;
    private String userToken;

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
        
        // Create test tenant
        testTenant = new Tenant();
        testTenant.setTenantCode("test-tenant");
        testTenant.setName("Test Tenant");
        testTenant.setStatus(Tenant.TenantStatus.ACTIVE);
        testTenant = tenantRepository.save(testTenant);
        
        // Create admin user
        adminUser = new User();
        adminUser.setTenant(testTenant);
        adminUser.setEmail("admin@example.com");
        adminUser.setUsername("admin");
        adminUser.setStatus(User.UserStatus.ACTIVE);
        adminUser = userRepository.save(adminUser);
        
        // Create regular user
        regularUser = new User();
        regularUser.setTenant(testTenant);
        regularUser.setEmail("user@example.com");
        regularUser.setUsername("user");
        regularUser.setStatus(User.UserStatus.ACTIVE);
        regularUser = userRepository.save(regularUser);
        
        // Create admin role
        UserRole adminRole = new UserRole();
        adminRole.setUser(adminUser);
        adminRole.setRoleName("SYSTEM_ADMIN");
        adminRole.setScope("global");
        adminRole.setStatus(UserRole.RoleStatus.ACTIVE);
        roleRepository.save(adminRole);
        
        // Create user role
        UserRole userRole = new UserRole();
        userRole.setUser(regularUser);
        userRole.setRoleName("USER");
        userRole.setScope("self");
        userRole.setStatus(UserRole.RoleStatus.ACTIVE);
        roleRepository.save(userRole);
        
        // Get authentication tokens
        adminToken = authenticateUser(adminUser.getEmail(), "admin_password");
        userToken = authenticateUser(regularUser.getEmail(), "user_password");
    }

    @Test
    @DisplayName("FR3.1 - Policy engine with ABAC/ReBAC and explicit deny precedence")
    void testPolicyEngineWithABACReBACAndPrecedence() {
        // Create ABAC policy (attribute-based)
        TenantPolicy abacPolicy = new TenantPolicy();
        abacPolicy.setTenant(testTenant);
        abacPolicy.setName("ABAC Document Access Policy");
        abacPolicy.setPolicyType(TenantPolicy.PolicyType.AUTHORIZATION);
        abacPolicy.setEffect(TenantPolicy.Effect.ALLOW);
        abacPolicy.setPriority(100);
        abacPolicy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        abacPolicy.setSubjects(new String[]{"role:USER"});
        abacPolicy.setResources(new String[]{"document:*"});
        abacPolicy.setActions(new String[]{"read"});
        abacPolicy.setConditions("{\"department\":\"engineering\",\"clearanceLevel\":\"confidential\"}");
        policyRepository.save(abacPolicy);
        
        // Create ReBAC policy (relationship-based)
        TenantPolicy rebacPolicy = new TenantPolicy();
        rebacPolicy.setTenant(testTenant);
        rebacPolicy.setName("ReBAC Project Access Policy");
        rebacPolicy.setPolicyType(TenantPolicy.PolicyType.AUTHORIZATION);
        rebacPolicy.setEffect(TenantPolicy.Effect.ALLOW);
        rebacPolicy.setPriority(200);
        rebacPolicy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        rebacPolicy.setSubjects(new String[]{"role:USER"});
        rebacPolicy.setResources(new String[]{"project:shared:*"});
        rebacPolicy.setActions(new String[]{"read", "write"});
        rebacPolicy.setConditions("{\"relationship\":\"member\"}");
        policyRepository.save(rebacPolicy);
        
        // Create explicit deny policy (should override allow)
        TenantPolicy denyPolicy = new TenantPolicy();
        denyPolicy.setTenant(testTenant);
        denyPolicy.setName("Explicit Deny Sensitive Data");
        denyPolicy.setPolicyType(TenantPolicy.PolicyType.AUTHORIZATION);
        denyPolicy.setEffect(TenantPolicy.Effect.DENY);
        denyPolicy.setPriority(1); // Highest priority
        denyPolicy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        denyPolicy.setSubjects(new String[]{"role:USER"});
        denyPolicy.setResources(new String[]{"document:sensitive:*"});
        denyPolicy.setActions(new String[]{"*"});
        policyRepository.save(denyPolicy);
        
        // Test ABAC authorization
        Map<String, Object> abacRequest = Map.of(
            "subject", "role:USER",
            "resource", "document:report.pdf",
            "action", "read",
            "context", Map.of(
                "department", "engineering",
                "clearanceLevel", "confidential"
            )
        );
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        HttpEntity<Map<String, Object>> abacEntity = new HttpEntity<>(abacRequest, headers);
        
        ResponseEntity<Map> abacResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            abacEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, abacResponse.getStatusCode());
        Map<String, Object> abacResult = abacResponse.getBody();
        assertEquals("ALLOW", abacResult.get("decision"));
        
        // Test ReBAC authorization
        Map<String, Object> rebacRequest = Map.of(
            "subject", "role:USER",
            "resource", "project:shared:documents",
            "action", "write",
            "context", Map.of(
                "relationship", "member"
            )
        );
        
        HttpEntity<Map<String, Object>> rebacEntity = new HttpEntity<>(rebacRequest, headers);
        
        ResponseEntity<Map> rebacResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            rebacEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, rebacResponse.getStatusCode());
        Map<String, Object> rebacResult = rebacResponse.getBody();
        assertEquals("ALLOW", rebacResult.get("decision"));
        
        // Test explicit deny precedence
        Map<String, Object> denyRequest = Map.of(
            "subject", "role:USER",
            "resource", "document:sensitive:classified.pdf",
            "action", "read",
            "context", Map.of(
                "department", "engineering",
                "clearanceLevel", "confidential"
            )
        );
        
        HttpEntity<Map<String, Object>> denyEntity = new HttpEntity<>(denyRequest, headers);
        
        ResponseEntity<Map> denyResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            denyEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, denyResponse.getStatusCode());
        Map<String, Object> denyResult = denyResponse.getBody();
        assertEquals("DENY", denyResult.get("decision"));
        assertTrue(denyResult.get("reason").toString().contains("explicit deny"));
    }

    @Test
    @DisplayName("FR3.2 - Contextual Authorization API with batch decisions and TOCTOU protection")
    void testContextualAuthorizationAPIWithBatchDecisions() {
        // Create policy for batch testing
        TenantPolicy batchPolicy = new TenantPolicy();
        batchPolicy.setTenant(testTenant);
        batchPolicy.setName("Batch Authorization Policy");
        batchPolicy.setPolicyType(TenantPolicy.PolicyType.AUTHORIZATION);
        batchPolicy.setEffect(TenantPolicy.Effect.ALLOW);
        batchPolicy.setPriority(100);
        batchPolicy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        batchPolicy.setSubjects(new String[]{"role:USER"});
        batchPolicy.setResources(new String[]{"file:*"});
        batchPolicy.setActions(new String[]{"read", "write"});
        policyRepository.save(batchPolicy);
        
        // Test batch authorization decisions
        Map<String, Object> batchRequest = Map.of(
            "requests", List.of(
                Map.of(
                    "subject", "role:USER",
                    "resource", "file:document1.pdf",
                    "action", "read"
                ),
                Map.of(
                    "subject", "role:USER",
                    "resource", "file:document2.pdf",
                    "action", "write"
                ),
                Map.of(
                    "subject", "role:USER",
                    "resource", "file:document3.pdf",
                    "action", "delete"
                )
            )
        );
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        HttpEntity<Map<String, Object>> batchEntity = new HttpEntity<>(batchRequest, headers);
        
        ResponseEntity<Map> batchResponse = restTemplate.exchange(
            baseUrl + "/authz/batch",
            HttpMethod.POST,
            batchEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, batchResponse.getStatusCode());
        Map<String, Object> batchResult = batchResponse.getBody();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) batchResult.get("decisions");
        assertEquals(3, decisions.size());
        
        // First two should be ALLOW (read/write are permitted)
        assertEquals("ALLOW", decisions.get(0).get("decision"));
        assertEquals("ALLOW", decisions.get(1).get("decision"));
        
        // Third should be DENY (delete not permitted by policy)
        assertEquals("DENY", decisions.get(2).get("decision"));
        
        // Test TOCTOU protection with timestamps
        long currentTime = System.currentTimeMillis();
        Map<String, Object> toctouRequest = Map.of(
            "subject", "role:USER",
            "resource", "file:important.pdf",
            "action", "read",
            "timestamp", currentTime
        );
        
        HttpEntity<Map<String, Object>> toctouEntity = new HttpEntity<>(toctouRequest, headers);
        
        ResponseEntity<Map> toctouResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            toctouEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, toctouResponse.getStatusCode());
        Map<String, Object> toctouResult = toctouResponse.getBody();
        
        // Should include timestamp validation
        assertNotNull(toctouResult.get("validUntil"));
        assertTrue((Long) toctouResult.get("validUntil") > currentTime);
    }

    @Test
    @DisplayName("FR3.4 - Time-bound, JIT & Emergency Access with break-glass accounts")
    void testTimeBoundJITAndEmergencyAccess() {
        // Create break-glass role
        UserRole breakGlassRole = new UserRole();
        breakGlassRole.setUser(regularUser);
        breakGlassRole.setRoleName("EMERGENCY_ADMIN");
        breakGlassRole.setScope("emergency");
        breakGlassRole.setAssignmentType(UserRole.AssignmentType.JIT);
        breakGlassRole.setBreakGlassRole(true);
        breakGlassRole.setEmergencyAccess(true);
        breakGlassRole.setApprovalRequired(true);
        breakGlassRole.setStatus(UserRole.RoleStatus.PENDING_APPROVAL);
        breakGlassRole.setJustification("Critical system outage - database corruption detected");
        breakGlassRole.setExpiresAt(Instant.now().plusMinutes(30)); // 30-minute emergency access
        breakGlassRole = roleRepository.save(breakGlassRole);
        
        // Request break-glass access activation
        Map<String, Object> breakGlassRequest = Map.of(
            "roleId", breakGlassRole.getId().toString(),
            "justification", "Critical system outage requires immediate access",
            "approvalRequired", true
        );
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userToken);
        HttpEntity<Map<String, Object>> bgEntity = new HttpEntity<>(breakGlassRequest, headers);
        
        ResponseEntity<Map> bgResponse = restTemplate.exchange(
            baseUrl + "/authz/break-glass/request",
            HttpMethod.POST,
            bgEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, bgResponse.getStatusCode());
        Map<String, Object> bgResult = bgResponse.getBody();
        assertEquals("PENDING_APPROVAL", bgResult.get("status"));
        assertNotNull(bgResult.get("approvalWorkflowId"));
        
        // Simulate admin approval
        Map<String, Object> approvalRequest = Map.of(
            "roleId", breakGlassRole.getId().toString(),
            "approved", true,
            "approverComment", "Emergency access approved due to system outage"
        );
        
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        HttpEntity<Map<String, Object>> approvalEntity = new HttpEntity<>(approvalRequest, adminHeaders);
        
        ResponseEntity<Map> approvalResponse = restTemplate.exchange(
            baseUrl + "/authz/break-glass/approve",
            HttpMethod.POST,
            approvalEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, approvalResponse.getStatusCode());
        Map<String, Object> approvalResult = approvalResponse.getBody();
        assertEquals("APPROVED", approvalResult.get("status"));
        
        // Test emergency access
        Map<String, Object> emergencyAuthzRequest = Map.of(
            "subject", "user:" + regularUser.getId(),
            "resource", "system:emergency:*",
            "action", "admin",
            "context", Map.of(
                "emergency", true,
                "breakGlassActive", true
            )
        );
        
        HttpEntity<Map<String, Object>> emergencyEntity = new HttpEntity<>(emergencyAuthzRequest, headers);
        
        ResponseEntity<Map> emergencyResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            emergencyEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, emergencyResponse.getStatusCode());
        Map<String, Object> emergencyResult = emergencyResponse.getBody();
        assertEquals("ALLOW", emergencyResult.get("decision"));
        assertTrue(emergencyResult.get("reason").toString().contains("break-glass"));
        
        // Verify audit events were created
        var auditEvents = auditEventRepository.findByUserId(regularUser.getId());
        boolean hasBreakGlassEvent = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals(AuditEvent.EventTypes.BREAK_GLASS_ACCESS));
        assertTrue(hasBreakGlassEvent);
    }

    @Test
    @DisplayName("FR3.5 - Advanced delegation and scoped administration")
    void testAdvancedDelegationAndScopedAdministration() {
        // Create original role with delegation capability
        UserRole originalRole = new UserRole();
        originalRole.setUser(adminUser);
        originalRole.setRoleName("PROJECT_ADMIN");
        originalRole.setScope("project:alpha");
        originalRole.setMaxDelegationDepth(2);
        originalRole.setDelegationDepth(0);
        originalRole.setStatus(UserRole.RoleStatus.ACTIVE);
        originalRole = roleRepository.save(originalRole);
        
        // Delegate role to regular user
        Map<String, Object> delegationRequest = Map.of(
            "fromUserId", adminUser.getId().toString(),
            "toUserId", regularUser.getId().toString(),
            "roleId", originalRole.getId().toString(),
            "scope", "project:alpha:documents",
            "restrictions", Map.of(
                "actions", List.of("read", "write"),
                "excludeActions", List.of("delete", "admin"),
                "maxDelegationDepth", 1
            ),
            "justification", "Temporary access for project documentation update"
        );
        
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        HttpEntity<Map<String, Object>> delegationEntity = new HttpEntity<>(delegationRequest, adminHeaders);
        
        ResponseEntity<Map> delegationResponse = restTemplate.exchange(
            baseUrl + "/authz/delegate",
            HttpMethod.POST,
            delegationEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, delegationResponse.getStatusCode());
        Map<String, Object> delegationResult = delegationResponse.getBody();
        assertEquals("DELEGATED", delegationResult.get("status"));
        String delegatedRoleId = (String) delegationResult.get("delegatedRoleId");
        assertNotNull(delegatedRoleId);
        
        // Test delegated permissions
        Map<String, Object> delegatedAuthzRequest = Map.of(
            "subject", "user:" + regularUser.getId(),
            "resource", "project:alpha:documents:report.pdf",
            "action", "write",
            "context", Map.of(
                "delegated", true
            )
        );
        
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(userToken);
        HttpEntity<Map<String, Object>> delegatedEntity = new HttpEntity<>(delegatedAuthzRequest, userHeaders);
        
        ResponseEntity<Map> delegatedResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            delegatedEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, delegatedResponse.getStatusCode());
        Map<String, Object> delegatedAuthzResult = delegatedResponse.getBody();
        assertEquals("ALLOW", delegatedAuthzResult.get("decision"));
        
        // Test restriction enforcement - delete should be denied
        Map<String, Object> restrictedRequest = Map.of(
            "subject", "user:" + regularUser.getId(),
            "resource", "project:alpha:documents:report.pdf",
            "action", "delete",
            "context", Map.of(
                "delegated", true
            )
        );
        
        HttpEntity<Map<String, Object>> restrictedEntity = new HttpEntity<>(restrictedRequest, userHeaders);
        
        ResponseEntity<Map> restrictedResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            restrictedEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, restrictedResponse.getStatusCode());
        Map<String, Object> restrictedResult = restrictedResponse.getBody();
        assertEquals("DENY", restrictedResult.get("decision"));
        assertTrue(restrictedResult.get("reason").toString().contains("delegation restriction"));
        
        // Test delegation revocation
        Map<String, Object> revocationRequest = Map.of(
            "delegatedRoleId", delegatedRoleId,
            "reason", "Project completed - revoking delegated access"
        );
        
        HttpEntity<Map<String, Object>> revocationEntity = new HttpEntity<>(revocationRequest, adminHeaders);
        
        ResponseEntity<Map> revocationResponse = restTemplate.exchange(
            baseUrl + "/authz/delegate/" + delegatedRoleId + "/revoke",
            HttpMethod.POST,
            revocationEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, revocationResponse.getStatusCode());
        Map<String, Object> revocationResult = revocationResponse.getBody();
        assertEquals("REVOKED", revocationResult.get("status"));
        
        // Verify delegated access is now denied
        ResponseEntity<Map> postRevocationResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            delegatedEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, postRevocationResponse.getStatusCode());
        Map<String, Object> postRevocationResult = postRevocationResponse.getBody();
        assertEquals("DENY", postRevocationResult.get("decision"));
    }

    @Test
    @DisplayName("FR3.6 - Granular consent management on per-resource and per-action basis")
    void testGranularConsentManagement() {
        // Create consent policy
        TenantPolicy consentPolicy = new TenantPolicy();
        consentPolicy.setTenant(testTenant);
        consentPolicy.setName("Data Sharing Consent Policy");
        consentPolicy.setPolicyType(TenantPolicy.PolicyType.COMPLIANCE);
        consentPolicy.setEffect(TenantPolicy.Effect.ALLOW);
        consentPolicy.setConsentRequired(true);
        consentPolicy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        consentPolicy.setSubjects(new String[]{"application:*"});
        consentPolicy.setResources(new String[]{"user:profile:*", "user:contacts:*"});
        consentPolicy.setActions(new String[]{"read", "export"});
        policyRepository.save(consentPolicy);
        
        // Grant granular consent
        Map<String, Object> consentRequest = Map.of(
            "userId", regularUser.getId().toString(),
            "application", "crm-integration",
            "consents", List.of(
                Map.of(
                    "resource", "user:profile:email",
                    "actions", List.of("read"),
                    "purpose", "Contact synchronization"
                ),
                Map.of(
                    "resource", "user:profile:name",
                    "actions", List.of("read"),
                    "purpose", "Personalization"
                ),
                Map.of(
                    "resource", "user:contacts:*",
                    "actions", List.of("read"),
                    "purpose", "Contact list synchronization"
                )
            )
        );
        
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(userToken);
        HttpEntity<Map<String, Object>> consentEntity = new HttpEntity<>(consentRequest, userHeaders);
        
        ResponseEntity<Map> consentResponse = restTemplate.exchange(
            baseUrl + "/privacy/consent",
            HttpMethod.POST,
            consentEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, consentResponse.getStatusCode());
        Map<String, Object> consentResult = consentResponse.getBody();
        assertEquals("CONSENT_GRANTED", consentResult.get("status"));
        
        // Test authorized access with consent
        Map<String, Object> authorizedRequest = Map.of(
            "subject", "application:crm-integration",
            "resource", "user:profile:email",
            "action", "read",
            "context", Map.of(
                "userId", regularUser.getId().toString(),
                "consentRequired", true
            )
        );
        
        HttpEntity<Map<String, Object>> authorizedEntity = new HttpEntity<>(authorizedRequest, userHeaders);
        
        ResponseEntity<Map> authorizedResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            authorizedEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, authorizedResponse.getStatusCode());
        Map<String, Object> authorizedResult = authorizedResponse.getBody();
        assertEquals("ALLOW", authorizedResult.get("decision"));
        assertTrue(authorizedResult.get("reason").toString().contains("consent granted"));
        
        // Test unauthorized access (no consent for phone number)
        Map<String, Object> unauthorizedRequest = Map.of(
            "subject", "application:crm-integration",
            "resource", "user:profile:phone",
            "action", "read",
            "context", Map.of(
                "userId", regularUser.getId().toString(),
                "consentRequired", true
            )
        );
        
        HttpEntity<Map<String, Object>> unauthorizedEntity = new HttpEntity<>(unauthorizedRequest, userHeaders);
        
        ResponseEntity<Map> unauthorizedResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            unauthorizedEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, unauthorizedResponse.getStatusCode());
        Map<String, Object> unauthorizedResult = unauthorizedResponse.getBody();
        assertEquals("DENY", unauthorizedResult.get("decision"));
        assertTrue(unauthorizedResult.get("reason").toString().contains("consent not granted"));
        
        // Test consent withdrawal
        Map<String, Object> withdrawalRequest = Map.of(
            "userId", regularUser.getId().toString(),
            "application", "crm-integration",
            "resource", "user:profile:email"
        );
        
        HttpEntity<Map<String, Object>> withdrawalEntity = new HttpEntity<>(withdrawalRequest, userHeaders);
        
        ResponseEntity<Map> withdrawalResponse = restTemplate.exchange(
            baseUrl + "/privacy/consent/withdraw",
            HttpMethod.POST,
            withdrawalEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, withdrawalResponse.getStatusCode());
        Map<String, Object> withdrawalResult = withdrawalResponse.getBody();
        assertEquals("CONSENT_WITHDRAWN", withdrawalResult.get("status"));
        
        // Verify access is now denied after withdrawal
        ResponseEntity<Map> postWithdrawalResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            authorizedEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, postWithdrawalResponse.getStatusCode());
        Map<String, Object> postWithdrawalResult = postWithdrawalResponse.getBody();
        assertEquals("DENY", postWithdrawalResult.get("decision"));
        assertTrue(postWithdrawalResult.get("reason").toString().contains("consent withdrawn"));
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
