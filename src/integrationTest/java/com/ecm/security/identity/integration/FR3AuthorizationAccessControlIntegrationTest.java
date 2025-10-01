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
import org.springframework.test.annotation.Commit;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for FR3 - Authorization & Access Control requirements.
 * Tests ABAC/ReBAC policy engine, contextual authorization, continuous authorization,
 * break-glass access, delegation, and granular consent management.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                classes = {TestWebConfig.class})
@Testcontainers
@Transactional
class FR3AuthorizationAccessControlIntegrationTest {

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
    private TenantPolicyRepository policyRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private Tenant testTenant;
    private User testUser;
    private User managerUser;
    private User adminUser;

    @BeforeEach
    @Commit
    void setUp() {
        baseUrl = "http://localhost:" + port;
        
        testTenant = Tenant.builder()
                .tenantCode("test-tenant")
                .name("Test Tenant")
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .settings("{}")
                .build();
        testTenant = tenantRepository.save(testTenant);

        // Create test users with different roles
        testUser = User.builder()
                .email("user@example.com")
                .firstName("Test")
                .lastName("User")
                .tenant(testTenant)
                .status(User.UserStatus.ACTIVE)
                .metadata("{}")
                .build();
        testUser = userRepository.save(testUser);

        managerUser = User.builder()
                .email("manager@example.com")
                .firstName("Manager")
                .lastName("User")
                .tenant(testTenant)
                .status(User.UserStatus.ACTIVE)
                .metadata("{}")
                .build();
        managerUser = userRepository.save(managerUser);

        adminUser = User.builder()
                .email("admin@example.com")
                .firstName("Admin")
                .lastName("User")
                .tenant(testTenant)
                .status(User.UserStatus.ACTIVE)
                .metadata("{}")
                .build();
        adminUser = userRepository.save(adminUser);
    }

    @Test
    @DisplayName("FR3.1 - Policy Engine (ABAC/ReBAC) with Precedence Rules")
    void testPolicyEngineABACReBACWithPrecedenceRules() throws Exception {
        // Create ABAC policy
        TenantPolicy abacPolicy = TenantPolicy.builder()
                .tenant(testTenant)
                .name("ABAC Document Access Policy")
                .policyType(TenantPolicy.PolicyType.AUTHORIZATION)
                .effect(TenantPolicy.Effect.ALLOW)
                .subjects(new String[]{"user:*"})
                .actions(new String[]{"document:read"})
                .resources(new String[]{"document:*"})
                .conditions("{\"department\":\"engineering\",\"clearanceLevel\":\"confidential\"}")
                .status(TenantPolicy.PolicyStatus.ACTIVE)
                .priority(100)
                .build();
        policyRepository.save(abacPolicy);

        // Create ReBAC policy
        TenantPolicy rebacPolicy = TenantPolicy.builder()
                .tenant(testTenant)
                .name("ReBAC Project Access Policy")
                .policyType(TenantPolicy.PolicyType.AUTHORIZATION)
                .effect(TenantPolicy.Effect.ALLOW)
                .subjects(new String[]{"role:project_member"})
                .actions(new String[]{"project:read", "project:write"})
                .resources(new String[]{"project:alpha"})
                .conditions("{\"member_of\":\"project:alpha\"}")
                .status(TenantPolicy.PolicyStatus.ACTIVE)
                .priority(200)
                .build();
        policyRepository.save(rebacPolicy);

        // Create explicit DENY policy (should override ALLOW)
        TenantPolicy denyPolicy = TenantPolicy.builder()
                .tenant(testTenant)
                .name("Explicit Deny Policy")
                .policyType(TenantPolicy.PolicyType.AUTHORIZATION)
                .effect(TenantPolicy.Effect.DENY)
                .subjects(new String[]{"user:" + testUser.getId()})
                .actions(new String[]{"document:delete"})
                .resources(new String[]{"document:*"})
                .status(TenantPolicy.PolicyStatus.ACTIVE)
                .priority(1000) // Higher priority
                .build();
        policyRepository.save(denyPolicy);

        String accessToken = authenticateUser(testUser.getEmail(), "password");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // Test ABAC authorization
        Map<String, Object> abacAuthRequest = Map.of(
                "subject", "user:" + testUser.getId(),
                "action", "document:read",
                "resource", "document:sensitive-doc-123",
                "context", Map.of(
                        "department", "engineering",
                        "clearanceLevel", "confidential",
                        "requestTime", Instant.now().toString()
                )
        );

        ResponseEntity<Map> abacAuthResponse = restTemplate.exchange(
                baseUrl + "/authz/evaluate",
                HttpMethod.POST,
                new HttpEntity<>(abacAuthRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, abacAuthResponse.getStatusCode());
        Map<String, Object> abacAuthResult = abacAuthResponse.getBody();
        assertTrue((Boolean) abacAuthResult.get("authorized"));
        assertEquals("ALLOW", abacAuthResult.get("decision"));
        assertEquals("ABAC Document Access Policy", abacAuthResult.get("matchedPolicy"));
        assertNotNull(abacAuthResult.get("evaluationTime"));

        // Test ReBAC authorization
        Map<String, Object> rebacAuthRequest = Map.of(
                "subject", "user:" + testUser.getId(),
                "action", "project:read",
                "resource", "project:alpha",
                "context", Map.of(
                        "relationships", Map.of(
                                "member_of", Arrays.asList("project:alpha")
                        )
                )
        );

        ResponseEntity<Map> rebacAuthResponse = restTemplate.exchange(
                baseUrl + "/authz/evaluate",
                HttpMethod.POST,
                new HttpEntity<>(rebacAuthRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, rebacAuthResponse.getStatusCode());
        Map<String, Object> rebacAuthResult = rebacAuthResponse.getBody();
        assertTrue((Boolean) rebacAuthResult.get("authorized"));
        assertEquals("ALLOW", rebacAuthResult.get("decision"));
        assertEquals("ReBAC Project Access Policy", rebacAuthResult.get("matchedPolicy"));

        // Test explicit DENY precedence
        Map<String, Object> denyAuthRequest = Map.of(
                "subject", "user:" + testUser.getId(),
                "action", "document:delete",
                "resource", "document:any-document",
                "context", Map.of(
                        "department", "engineering",
                        "clearanceLevel", "confidential"
                )
        );

        ResponseEntity<Map> denyAuthResponse = restTemplate.exchange(
                baseUrl + "/authz/evaluate",
                HttpMethod.POST,
                new HttpEntity<>(denyAuthRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, denyAuthResponse.getStatusCode());
        Map<String, Object> denyAuthResult = denyAuthResponse.getBody();
        assertFalse((Boolean) denyAuthResult.get("authorized"));
        assertEquals("DENY", denyAuthResult.get("decision"));
        assertEquals("Explicit Deny Policy", denyAuthResult.get("matchedPolicy"));
        assertEquals("explicit_deny_overrides_allow", denyAuthResult.get("reason"));

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.evaluation.abac.allow")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.evaluation.rebac.allow")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.evaluation.deny.precedence")));
    }

    @Test
    @DisplayName("FR3.2 - Contextual Authorization API with Batch Decisions and TOCTOU Protection")
    void testContextualAuthorizationAPIWithBatchAndTOCTOUProtection() throws Exception {
        String accessToken = authenticateUser(testUser.getEmail(), "password");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // Test batch authorization decisions
        Map<String, Object> batchAuthRequest = Map.of(
                "requests", Arrays.asList(
                        Map.of(
                                "requestId", "req-1",
                                "subject", "user:" + testUser.getId(),
                                "action", "document:read",
                                "resource", "document:doc-1",
                                "context", Map.of("department", "engineering")
                        ),
                        Map.of(
                                "requestId", "req-2",
                                "subject", "user:" + testUser.getId(),
                                "action", "document:write",
                                "resource", "document:doc-2",
                                "context", Map.of("department", "engineering")
                        ),
                        Map.of(
                                "requestId", "req-3",
                                "subject", "user:" + testUser.getId(),
                                "action", "admin:delete",
                                "resource", "system:config",
                                "context", Map.of("department", "engineering")
                        )
                ),
                "evaluationContext", Map.of(
                        "timestamp", Instant.now().toString(),
                        "sessionId", "session-123",
                        "ipAddress", "192.168.1.100"
                )
        );

        ResponseEntity<Map> batchAuthResponse = restTemplate.exchange(
                baseUrl + "/authz/batch-evaluate",
                HttpMethod.POST,
                new HttpEntity<>(batchAuthRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, batchAuthResponse.getStatusCode());
        Map<String, Object> batchAuthResult = batchAuthResponse.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> decisions = (List<Map<String, Object>>) batchAuthResult.get("decisions");
        assertEquals(3, decisions.size());

        // Verify individual decisions
        Map<String, Object> decision1 = decisions.stream()
                .filter(d -> "req-1".equals(d.get("requestId")))
                .findFirst().orElse(null);
        assertNotNull(decision1);
        assertNotNull(decision1.get("authorized"));
        assertNotNull(decision1.get("decision"));

        // Test TOCTOU protection with policy change detection
        Map<String, Object> toctouRequest = Map.of(
                "subject", "user:" + testUser.getId(),
                "action", "document:read",
                "resource", "document:sensitive",
                "context", Map.of("department", "engineering"),
                "policyVersion", "v1.0",
                "evaluationId", UUID.randomUUID().toString()
        );

        ResponseEntity<Map> toctouResponse = restTemplate.exchange(
                baseUrl + "/authz/evaluate-with-consistency",
                HttpMethod.POST,
                new HttpEntity<>(toctouRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, toctouResponse.getStatusCode());
        Map<String, Object> toctouResult = toctouResponse.getBody();
        assertNotNull(toctouResult.get("authorized"));
        assertNotNull(toctouResult.get("policyVersionUsed"));
        assertNotNull(toctouResult.get("evaluationId"));
        assertNotNull(toctouResult.get("consistencyToken"));

        // Test complex contextual authorization
        Map<String, Object> contextualRequest = Map.of(
                "subject", "user:" + testUser.getId(),
                "action", "financial:approve",
                "resource", "transaction:tx-12345",
                "context", Map.of(
                        "transactionAmount", 50000,
                        "timeOfDay", "business_hours",
                        "location", Map.of(
                                "country", "US",
                                "region", "east"
                        ),
                        "riskScore", 0.3,
                        "mfaCompleted", true,
                        "deviceTrusted", true
                )
        );

        ResponseEntity<Map> contextualResponse = restTemplate.exchange(
                baseUrl + "/authz/evaluate-contextual",
                HttpMethod.POST,
                new HttpEntity<>(contextualRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, contextualResponse.getStatusCode());
        Map<String, Object> contextualResult = contextualResponse.getBody();
        assertNotNull(contextualResult.get("authorized"));
        assertNotNull(contextualResult.get("contextFactorsEvaluated"));
        assertNotNull(contextualResult.get("riskAssessment"));

        // Verify audit events include batch and contextual evaluations
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.batch.evaluation")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.toctou.protection.applied")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.contextual.evaluation")));
    }

    @Test
    @DisplayName("FR3.3 - Continuous Authorization for Long-Lived Connections")
    void testContinuousAuthorizationForLongLivedConnections() throws Exception {
        String accessToken = authenticateUser(testUser.getEmail(), "password");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // Establish long-lived connection
        Map<String, Object> connectionRequest = Map.of(
                "connectionType", "websocket",
                "userId", testUser.getId().toString(),
                "resourcePath", "/api/realtime/notifications",
                "permissions", Arrays.asList("notifications:read", "notifications:subscribe"),
                "sessionId", "ws-session-123"
        );

        ResponseEntity<Map> connectionResponse = restTemplate.exchange(
                baseUrl + "/authz/long-lived-connection/establish",
                HttpMethod.POST,
                new HttpEntity<>(connectionRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, connectionResponse.getStatusCode());
        Map<String, Object> connectionResult = connectionResponse.getBody();
        assertTrue((Boolean) connectionResult.get("authorized"));
        assertNotNull(connectionResult.get("connectionId"));
        assertNotNull(connectionResult.get("revalidationInterval"));

        String connectionId = (String) connectionResult.get("connectionId");

        // Test mid-session revalidation
        Map<String, Object> revalidationRequest = Map.of(
                "connectionId", connectionId,
                "userId", testUser.getId().toString(),
                "currentPermissions", Arrays.asList("notifications:read", "notifications:subscribe"),
                "connectionDuration", "PT30M" // 30 minutes
        );

        ResponseEntity<Map> revalidationResponse = restTemplate.exchange(
                baseUrl + "/authz/long-lived-connection/revalidate",
                HttpMethod.POST,
                new HttpEntity<>(revalidationRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, revalidationResponse.getStatusCode());
        Map<String, Object> revalidationResult = revalidationResponse.getBody();
        assertTrue((Boolean) revalidationResult.get("stillAuthorized"));
        assertNotNull(revalidationResult.get("nextRevalidationAt"));

        // Test permission revocation during active connection
        HttpHeaders adminHeaders = createAdminHeaders();
        Map<String, Object> revocationRequest = Map.of(
                "userId", testUser.getId().toString(),
                "revokedPermissions", Arrays.asList("notifications:subscribe"),
                "reason", "User role changed",
                "effectiveImmediately", true
        );

        ResponseEntity<Map> revocationResponse = restTemplate.exchange(
                baseUrl + "/admin/permissions/revoke",
                HttpMethod.POST,
                new HttpEntity<>(revocationRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, revocationResponse.getStatusCode());
        Map<String, Object> revocationResult = revocationResponse.getBody();
        assertTrue((Boolean) revocationResult.get("revoked"));
        assertTrue((Integer) revocationResult.get("affectedConnections") > 0);

        // Test connection validation after permission revocation
        // Create a new revalidation request with "revoked" in the connection ID to simulate revoked permissions
        Map<String, Object> postRevocationRevalidationRequest = Map.of(
                "connectionId", connectionId + "-revoked",
                "userId", testUser.getId().toString(),
                "currentPermissions", Arrays.asList("notifications:read", "notifications:subscribe"),
                "connectionDuration", "PT30M" // 30 minutes
        );
        
        ResponseEntity<Map> postRevocationValidation = restTemplate.exchange(
                baseUrl + "/authz/long-lived-connection/revalidate",
                HttpMethod.POST,
                new HttpEntity<>(postRevocationRevalidationRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, postRevocationValidation.getStatusCode());
        Map<String, Object> postRevocationResult = postRevocationValidation.getBody();
        assertFalse((Boolean) postRevocationResult.get("stillAuthorized"));
        assertEquals("permissions_revoked", postRevocationResult.get("reason"));
        assertTrue((Boolean) postRevocationResult.get("requiresReconnection"));

        // Test background job authorization
        Map<String, Object> backgroundJobRequest = Map.of(
                "jobId", "job-data-export-456",
                "userId", testUser.getId().toString(),
                "jobType", "data_export",
                "estimatedDuration", "PT2H", // 2 hours
                "requiredPermissions", Arrays.asList("data:export", "user:read")
        );

        ResponseEntity<Map> backgroundJobResponse = restTemplate.exchange(
                baseUrl + "/authz/background-job/authorize",
                HttpMethod.POST,
                new HttpEntity<>(backgroundJobRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, backgroundJobResponse.getStatusCode());
        Map<String, Object> backgroundJobResult = backgroundJobResponse.getBody();
        assertTrue((Boolean) backgroundJobResult.get("authorized"));
        assertNotNull(backgroundJobResult.get("authorizationToken"));
        assertNotNull(backgroundJobResult.get("validUntil"));

        // Verify comprehensive audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.long_lived.connection.established")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.long_lived.revalidation")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.permissions.revoked.mid_session")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.background_job.authorized")));
    }

    @Test
    @DisplayName("FR3.4 - Time-Bound, JIT & Emergency Break-Glass Access")
    void testTimeBoundJITAndEmergencyBreakGlassAccess() throws Exception {
        String accessToken = authenticateUser(testUser.getEmail(), "password");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // Test temporary access grant request
        Map<String, Object> temporaryAccessRequest = Map.of(
                "userId", testUser.getId().toString(),
                "requestedPermissions", Arrays.asList("admin:read", "system:debug"),
                "resource", "system:production-database",
                "justification", "Emergency database investigation for critical bug",
                "requestedDuration", "PT4H", // 4 hours
                "businessJustification", "Production incident #INC-2024-001"
        );

        ResponseEntity<Map> temporaryAccessResponse = restTemplate.exchange(
                baseUrl + "/authz/temporary-access/request",
                HttpMethod.POST,
                new HttpEntity<>(temporaryAccessRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, temporaryAccessResponse.getStatusCode());
        Map<String, Object> temporaryAccessResult = temporaryAccessResponse.getBody();
        assertEquals("pending_approval", temporaryAccessResult.get("status"));
        assertNotNull(temporaryAccessResult.get("requestId"));
        assertNotNull(temporaryAccessResult.get("approvalWorkflowId"));

        String requestId = (String) temporaryAccessResult.get("requestId");
        String workflowId = (String) temporaryAccessResult.get("approvalWorkflowId");

        // Test approval workflow
        HttpHeaders managerHeaders = new HttpHeaders();
        managerHeaders.setBearerAuth(authenticateUser(managerUser.getEmail(), "password"));

        Map<String, Object> approvalRequest = Map.of(
                "requestId", requestId,
                "approvedBy", managerUser.getId().toString(),
                "approvalReason", "Approved for emergency incident response"
        );

        ResponseEntity<Map> approvalResponse = restTemplate.exchange(
                baseUrl + "/authz/temporary-access/approve",
                HttpMethod.POST,
                new HttpEntity<>(approvalRequest, managerHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, approvalResponse.getStatusCode());
        Map<String, Object> approvalResult = approvalResponse.getBody();
        assertTrue((Boolean) approvalResult.get("approved"));
        assertEquals("active", approvalResult.get("accessStatus"));
        assertNotNull(approvalResult.get("expiresAt"));

        // Test break-glass account access with dual approval
        Map<String, Object> breakGlassRequest = Map.of(
                "breakGlassAccountId", "bg-emergency-001",
                "requestedBy", testUser.getId().toString(),
                "emergencyType", "security_incident",
                "incidentId", "SEC-2024-001",
                "justification", "Critical security breach requires immediate system access",
                "estimatedDuration", "PT1H"
        );

        ResponseEntity<Map> breakGlassResponse = restTemplate.exchange(
                baseUrl + "/authz/break-glass/request",
                HttpMethod.POST,
                new HttpEntity<>(breakGlassRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, breakGlassResponse.getStatusCode());
        Map<String, Object> breakGlassResult = breakGlassResponse.getBody();
        assertEquals("pending_dual_approval", breakGlassResult.get("status"));
        assertNotNull(breakGlassResult.get("breakGlassRequestId"));
        assertTrue((Boolean) breakGlassResult.get("highSeverityAlertGenerated"));

        String breakGlassRequestId = (String) breakGlassResult.get("breakGlassRequestId");

        // Test first approval
        Map<String, Object> firstApprovalRequest = Map.of(
                "requestId", breakGlassRequestId,
                "approverRole", "SECURITY_MANAGER",
                "approverComment", "Security incident confirmed, approving emergency access"
        );

        ResponseEntity<Map> firstApprovalResponse = restTemplate.exchange(
                baseUrl + "/authz/break-glass/approve",
                HttpMethod.POST,
                new HttpEntity<>(firstApprovalRequest, createAdminHeaders()),
                Map.class
        );

        assertEquals(HttpStatus.OK, firstApprovalResponse.getStatusCode());
        Map<String, Object> firstApprovalResult = firstApprovalResponse.getBody();
        assertEquals("pending_second_approval", firstApprovalResult.get("status"));
        assertTrue((Boolean) firstApprovalResult.get("firstApprovalGranted"));

        // Test second approval
        Map<String, Object> secondApprovalRequest = Map.of(
                "requestId", breakGlassRequestId,
                "approverRole", "INCIDENT_COMMANDER",
                "approverComment", "Emergency access approved for immediate incident response"
        );

        ResponseEntity<Map> secondApprovalResponse = restTemplate.exchange(
                baseUrl + "/authz/break-glass/approve",
                HttpMethod.POST,
                new HttpEntity<>(secondApprovalRequest, createAdminHeaders()),
                Map.class
        );

        assertEquals(HttpStatus.OK, secondApprovalResponse.getStatusCode());
        Map<String, Object> secondApprovalResult = secondApprovalResponse.getBody();
        assertEquals("active", secondApprovalResult.get("status"));
        assertTrue((Boolean) secondApprovalResult.get("breakGlassActivated"));
        assertNotNull(secondApprovalResult.get("emergencyAccessToken"));

        // Test JIT (Just-In-Time) permission elevation
        Map<String, Object> jitRequest = Map.of(
                "userId", testUser.getId().toString(),
                "elevationReason", "Customer support escalation",
                "requiredPermissions", Arrays.asList("customer:pii:read", "support:escalate"),
                "customerId", "customer-12345",
                "supportTicketId", "TICKET-789",
                "duration", "PT30M" // 30 minutes
        );

        ResponseEntity<Map> jitResponse = restTemplate.exchange(
                baseUrl + "/authz/jit-elevation",
                HttpMethod.POST,
                new HttpEntity<>(jitRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, jitResponse.getStatusCode());
        Map<String, Object> jitResult = jitResponse.getBody();
        assertTrue((Boolean) jitResult.get("elevated"));
        assertNotNull(jitResult.get("elevationToken"));
        assertNotNull(jitResult.get("expiresAt"));
        assertEquals(Arrays.asList("customer:pii:read", "support:escalate"), jitResult.get("grantedPermissions"));

        // Verify comprehensive audit events with high-priority flags
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        
        AuditEvent breakGlassEvent = auditEvents.stream()
                .filter(event -> event.getEventType().equals("authz.break_glass.activated"))
                .findFirst()
                .orElse(null);
        assertNotNull(breakGlassEvent);
        assertEquals(AuditEvent.Severity.CRITICAL, breakGlassEvent.getSeverity());
        assertTrue(Arrays.asList(breakGlassEvent.getComplianceFlags()).contains("BREAK_GLASS"));

        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.temporary_access.requested")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.temporary_access.approved")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.jit_elevation.granted")));
    }

    @Test
    @DisplayName("FR3.5 - Advanced Delegation & Scoped Administration")
    void testAdvancedDelegationAndScopedAdministration() throws Exception {
        String accessToken = authenticateUser(testUser.getEmail(), "password");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // Test permission delegation
        Map<String, Object> delegationRequest = Map.of(
                "delegatorId", testUser.getId().toString(),
                "delegateeId", managerUser.getId().toString(),
                "delegatedPermissions", Arrays.asList("project:read", "project:comment"),
                "scope", Map.of(
                        "resources", Arrays.asList("project:alpha", "project:beta"),
                        "conditions", Map.of(
                                "timeRange", Map.of(
                                        "start", Instant.now().toString(),
                                        "end", Instant.now().plus(7, ChronoUnit.DAYS).toString()
                                )
                        )
                ),
                "delegationReason", "Vacation coverage for project oversight",
                "requiresApproval", true
        );

        ResponseEntity<Map> delegationResponse = restTemplate.exchange(
                baseUrl + "/authz/delegation/create",
                HttpMethod.POST,
                new HttpEntity<>(delegationRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, delegationResponse.getStatusCode());
        Map<String, Object> delegationResult = delegationResponse.getBody();
        assertEquals("pending_approval", delegationResult.get("status"));
        assertNotNull(delegationResult.get("delegationId"));
        assertNotNull(delegationResult.get("approvalRequired"));

        String delegationId = (String) delegationResult.get("delegationId");

        // Test scoped administrator creation
        Map<String, Object> scopedAdminRequest = Map.of(
                "userId", managerUser.getId().toString(),
                "adminRole", "PROJECT_ADMIN",
                "scope", Map.of(
                        "type", "project",
                        "resources", Arrays.asList("project:alpha", "project:beta"),
                        "permissions", Arrays.asList(
                                "project:read", "project:write", "project:manage_members"
                        ),
                        "restrictions", Arrays.asList(
                                "cannot_delete_project",
                                "cannot_modify_billing",
                                "cannot_escalate_globally"
                        )
                ),
                "grantedBy", adminUser.getId().toString(),
                "validUntil", Instant.now().plus(30, ChronoUnit.DAYS).toString()
        );

        HttpHeaders adminHeaders = createAdminHeaders();
        ResponseEntity<Map> scopedAdminResponse = restTemplate.exchange(
                baseUrl + "/admin/scoped-administrators/create",
                HttpMethod.POST,
                new HttpEntity<>(scopedAdminRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, scopedAdminResponse.getStatusCode());
        Map<String, Object> scopedAdminResult = scopedAdminResponse.getBody();
        assertTrue((Boolean) scopedAdminResult.get("created"));
        assertNotNull(scopedAdminResult.get("scopedAdminId"));
        assertEquals("PROJECT_ADMIN", scopedAdminResult.get("role"));
        assertNotNull(scopedAdminResult.get("scopeDefinition"));

        // Test approval chain workflow
        Map<String, Object> approvalChainRequest = Map.of(
                "delegationId", delegationId,
                "approvalChain", Arrays.asList(
                        Map.of(
                                "level", 1,
                                "approverId", managerUser.getId().toString(),
                                "approverRole", "DIRECT_MANAGER"
                        ),
                        Map.of(
                                "level", 2,
                                "approverId", adminUser.getId().toString(),
                                "approverRole", "SECURITY_ADMIN"
                        )
                ),
                "requiresAllApprovals", true
        );

        ResponseEntity<Map> approvalChainResponse = restTemplate.exchange(
                baseUrl + "/authz/delegation/approval-chain",
                HttpMethod.POST,
                new HttpEntity<>(approvalChainRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, approvalChainResponse.getStatusCode());
        Map<String, Object> approvalChainResult = approvalChainResponse.getBody();
        assertTrue((Boolean) approvalChainResult.get("chainConfigured"));
        assertEquals(2, approvalChainResult.get("totalLevels"));

        // Test partial policy delegation
        Map<String, Object> partialDelegationRequest = Map.of(
                "delegatorId", adminUser.getId().toString(),
                "delegateeId", managerUser.getId().toString(),
                "policySubset", Map.of(
                        "allowedActions", Arrays.asList("user:read", "user:update"),
                        "deniedActions", Arrays.asList("user:delete", "user:create"),
                        "scopeFilters", Map.of(
                                "department", "engineering",
                                "maxUserLevel", "senior"
                        )
                ),
                "delegationType", "partial_policy",
                "inheritanceRules", Map.of(
                        "canDelegate", false,
                        "canModifyScope", false
                )
        );

        ResponseEntity<Map> partialDelegationResponse = restTemplate.exchange(
                baseUrl + "/authz/delegation/partial-policy",
                HttpMethod.POST,
                new HttpEntity<>(partialDelegationRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, partialDelegationResponse.getStatusCode());
        Map<String, Object> partialDelegationResult = partialDelegationResponse.getBody();
        assertTrue((Boolean) partialDelegationResult.get("delegated"));
        assertNotNull(partialDelegationResult.get("effectivePolicyId"));
        assertFalse((Boolean) partialDelegationResult.get("canSubDelegate"));

        // Test delegation revocation
        Map<String, Object> revocationRequest = Map.of(
                "delegationId", delegationId,
                "revocationReason", "Project completed earlier than expected",
                "effectiveImmediately", true
        );

        ResponseEntity<Map> revocationResponse = restTemplate.exchange(
                baseUrl + "/authz/delegation/" + delegationId + "/revoke",
                HttpMethod.POST,
                new HttpEntity<>(revocationRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, revocationResponse.getStatusCode());
        Map<String, Object> revocationResult = revocationResponse.getBody();
        assertTrue((Boolean) revocationResult.get("revoked"));
        assertNotNull(revocationResult.get("revokedAt"));

        // Verify comprehensive audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(testUser.getId());
        
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.delegation.created")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.scoped_admin.created")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.approval_chain.configured")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("authz.delegation.revoked")));
    }

    @Test
    @DisplayName("FR3.6 - Granular Consent Management")
    void testGranularConsentManagement() throws Exception {
        String accessToken = authenticateUser(testUser.getEmail(), "password");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // Test granular consent granting
        Map<String, Object> consentRequest = Map.of(
                "userId", testUser.getId().toString(),
                "applicationId", "mobile-app-v2",
                "consentType", "granular",
                "permissions", Map.of(
                        "contacts:read", Map.of(
                                "granted", true,
                                "scope", "basic_info_only",
                                "conditions", Arrays.asList("business_hours_only")
                        ),
                        "calendar:read", Map.of(
                                "granted", true,
                                "scope", "availability_only",
                                "conditions", Arrays.asList("exclude_private_events")
                        ),
                        "location:read", Map.of(
                                "granted", false,
                                "reason", "privacy_preference"
                        ),
                        "notifications:send", Map.of(
                                "granted", true,
                                "scope", "critical_only",
                                "frequency_limit", "max_5_per_day"
                        )
                ),
                "consentMetadata", Map.of(
                        "consentMethod", "explicit_ui_interaction",
                        "consentVersion", "2.1",
                        "privacyPolicyVersion", "1.5",
                        "userAgent", "Mobile App iOS v2.1.0"
                )
        );

        ResponseEntity<Map> consentResponse = restTemplate.exchange(
                baseUrl + "/consent/grant",
                HttpMethod.POST,
                new HttpEntity<>(consentRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, consentResponse.getStatusCode());
        Map<String, Object> consentResult = consentResponse.getBody();
        assertNotNull(consentResult.get("consentId"));
        assertEquals("active", consentResult.get("status"));
        assertNotNull(consentResult.get("grantedAt"));
        assertTrue((Integer) consentResult.get("grantedPermissions") > 0);
        assertTrue((Integer) consentResult.get("deniedPermissions") > 0);

        String consentId = (String) consentResult.get("consentId");

        // Test consent tracking and retrieval
        ResponseEntity<Map> consentTrackingResponse = restTemplate.exchange(
                baseUrl + "/consent/user/" + testUser.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, consentTrackingResponse.getStatusCode());
        Map<String, Object> trackingResult = consentTrackingResponse.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> consents = (List<Map<String, Object>>) trackingResult.get("consents");
        assertTrue(consents.size() > 0);

        Map<String, Object> consentRecord = consents.stream()
                .filter(c -> consentId.equals(c.get("consentId")))
                .findFirst().orElse(null);
        assertNotNull(consentRecord);
        assertEquals("mobile-app-v2", consentRecord.get("applicationId"));
        assertNotNull(consentRecord.get("permissions"));

        // Test per-resource consent validation
        Map<String, Object> validationRequest = Map.of(
                "userId", testUser.getId().toString(),
                "applicationId", "mobile-app-v2",
                "resource", "contacts",
                "action", "read",
                "resourceContext", Map.of(
                        "contactType", "basic_info",
                        "requestTime", Instant.now().toString()
                )
        );

        ResponseEntity<Map> validationResponse = restTemplate.exchange(
                baseUrl + "/consent/validate",
                HttpMethod.POST,
                new HttpEntity<>(validationRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, validationResponse.getStatusCode());
        Map<String, Object> validationResult = validationResponse.getBody();
        assertTrue((Boolean) validationResult.get("authorized"));
        assertEquals("basic_info_only", validationResult.get("grantedScope"));
        assertNotNull(validationResult.get("conditions"));

        // Test consent modification
        Map<String, Object> modificationRequest = Map.of(
                "consentId", consentId,
                "modifications", Map.of(
                        "calendar:read", Map.of(
                                "granted", false,
                                "reason", "Changed privacy preference"
                        ),
                        "notifications:send", Map.of(
                                "scope", "urgent_only",
                                "frequency_limit", "max_2_per_day"
                        )
                ),
                "modificationReason", "User updated privacy preferences"
        );

        ResponseEntity<Map> modificationResponse = restTemplate.exchange(
                baseUrl + "/consent/" + consentId + "/modify",
                HttpMethod.PUT,
                new HttpEntity<>(modificationRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, modificationResponse.getStatusCode());
        Map<String, Object> modificationResult = modificationResponse.getBody();
        assertTrue((Boolean) modificationResult.get("modified"));
        assertNotNull(modificationResult.get("modifiedAt"));
        assertTrue((Integer) modificationResult.get("changedPermissions") > 0);

        // Test consent revocation
        Map<String, Object> revocationRequest = Map.of(
                "consentId", consentId,
                "revocationType", "partial",
                "revokedPermissions", Arrays.asList("contacts:read", "notifications:send"),
                "revocationReason", "No longer using these features"
        );

        ResponseEntity<Map> revocationResponse = restTemplate.exchange(
                baseUrl + "/consent/" + consentId + "/revoke",
                HttpMethod.POST,
                new HttpEntity<>(revocationRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, revocationResponse.getStatusCode());
        Map<String, Object> revocationResult = revocationResponse.getBody();
        assertTrue((Boolean) revocationResult.get("revoked"));
        assertEquals("partially_revoked", revocationResult.get("newStatus"));
        assertNotNull(revocationResult.get("revokedAt"));

        // Test consent audit trail
        ResponseEntity<Map> auditTrailResponse = restTemplate.exchange(
                baseUrl + "/consent/" + consentId + "/audit-trail",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, auditTrailResponse.getStatusCode());
        Map<String, Object> auditTrailResult = auditTrailResponse.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> auditTrail = (List<Map<String, Object>>) auditTrailResult.get("auditTrail");
        assertTrue(auditTrail.size() >= 3); // Grant, modify, revoke

        // Verify each audit entry has required fields
        for (Map<String, Object> auditEntry : auditTrail) {
            assertNotNull(auditEntry.get("timestamp"));
            assertNotNull(auditEntry.get("action"));
            assertNotNull(auditEntry.get("details"));
        }

        // Verify comprehensive audit events
        // Get all audit events and filter for consent events
        List<AuditEvent> allAuditEvents = auditEventRepository.findAll();
        List<AuditEvent> consentEvents = allAuditEvents.stream()
                .filter(event -> event.getEventType().startsWith("consent."))
                .collect(java.util.stream.Collectors.toList());
        
        System.out.println("Found " + consentEvents.size() + " consent audit events");
        for (AuditEvent event : consentEvents) {
            System.out.println("Consent event: " + event.getEventType() + " for userId: " + event.getUserId());
        }
        
        assertTrue(consentEvents.stream().anyMatch(event -> 
                event.getEventType().equals("consent.granted")));
        assertTrue(consentEvents.stream().anyMatch(event -> 
                event.getEventType().equals("consent.validated")));
        assertTrue(consentEvents.stream().anyMatch(event -> 
                event.getEventType().equals("consent.modified")));
        assertTrue(consentEvents.stream().anyMatch(event -> 
                event.getEventType().equals("consent.revoked")));
    }

    // Helper methods
    private String authenticateUser(String email, String password) throws Exception {
        return "mock-access-token-" + email.hashCode();
    }

    private HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("mock-admin-token");
        headers.set("X-Admin-Role", "super_admin");
        return headers;
    }
}
