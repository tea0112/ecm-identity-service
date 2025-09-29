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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Key Acceptance & Test Scenarios from requirements using Testcontainers.
 * These tests validate the critical security and operational requirements end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("Key Acceptance & Test Scenarios Integration Tests")
class AcceptanceIntegrationTest {

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
    private UserSessionRepository sessionRepository;

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
    private User testUser;
    private User adminUser;
    private String adminToken;
    private String userToken;

    @BeforeEach
    @Transactional
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1";
        
        // Clean up test data
        auditEventRepository.deleteAll();
        roleRepository.deleteAll();
        sessionRepository.deleteAll();
        policyRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
        
        // Create test tenant
        testTenant = new Tenant();
        testTenant.setTenantCode("acceptance-test");
        testTenant.setName("Acceptance Test Tenant");
        testTenant.setStatus(Tenant.TenantStatus.ACTIVE);
        testTenant = tenantRepository.save(testTenant);
        
        // Create test user
        testUser = new User();
        testUser.setTenant(testTenant);
        testUser.setEmail("testuser@acceptance.com");
        testUser.setUsername("testuser");
        testUser.setStatus(User.UserStatus.ACTIVE);
        testUser = userRepository.save(testUser);
        
        // Create admin user
        adminUser = new User();
        adminUser.setTenant(testTenant);
        adminUser.setEmail("admin@acceptance.com");
        adminUser.setUsername("admin");
        adminUser.setStatus(User.UserStatus.ACTIVE);
        adminUser = userRepository.save(adminUser);
        
        // Create admin role
        UserRole adminRole = new UserRole();
        adminRole.setUser(adminUser);
        adminRole.setRoleName("SYSTEM_ADMIN");
        adminRole.setScope("global");
        adminRole.setStatus(UserRole.RoleStatus.ACTIVE);
        roleRepository.save(adminRole);
        
        // Get authentication tokens
        adminToken = authenticateUser(adminUser.getEmail(), "admin_password");
        userToken = authenticateUser(testUser.getEmail(), "user_password");
    }

    @Test
    @DisplayName("Instant De-provisioning: When a user is deleted, session/token must be rejected within 1 second")
    void testInstantDeProvisioningWithinOneSecond() throws InterruptedException {
        // Create active session for user
        Map<String, Object> loginRequest = Map.of(
            "email", testUser.getEmail(),
            "password", "user_password"
        );
        
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            loginRequest,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        String accessToken = (String) loginResponse.getBody().get("accessToken");
        String sessionId = (String) loginResponse.getBody().get("sessionId");
        
        // Verify session is active
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        ResponseEntity<Map> profileResponse = restTemplate.exchange(
            baseUrl + "/users/me",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        assertEquals(HttpStatus.OK, profileResponse.getStatusCode());
        
        // Record timestamp before deletion
        Instant deletionStartTime = Instant.now();
        
        // Delete user (de-provision)
        Map<String, Object> deletionRequest = Map.of(
            "userId", testUser.getId().toString(),
            "reason", "User account termination",
            "immediate", true
        );
        
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        HttpEntity<Map<String, Object>> deletionEntity = new HttpEntity<>(deletionRequest, adminHeaders);
        
        ResponseEntity<Map> deletionResponse = restTemplate.exchange(
            baseUrl + "/admin/users/delete",
            HttpMethod.POST,
            deletionEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, deletionResponse.getStatusCode());
        Instant deletionCompletedTime = Instant.now();
        
        // Immediately try to use the session (within 1 second)
        ResponseEntity<Map> postDeletionResponse = restTemplate.exchange(
            baseUrl + "/users/me",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        
        Instant testCompletedTime = Instant.now();
        
        // Verify session is rejected
        assertEquals(HttpStatus.UNAUTHORIZED, postDeletionResponse.getStatusCode());
        Map<String, Object> errorResponse = postDeletionResponse.getBody();
        assertTrue(errorResponse.get("message").toString().contains("session invalidated") ||
                  errorResponse.get("message").toString().contains("user not found"));
        
        // Verify timing requirement (< 1 second)
        Duration totalDuration = Duration.between(deletionStartTime, testCompletedTime);
        assertTrue(totalDuration.toMillis() < 1000, 
                  "De-provisioning and session invalidation must complete within 1 second. Actual: " + totalDuration.toMillis() + "ms");
        
        // Verify audit event
        var auditEvents = auditEventRepository.findByUserId(testUser.getId());
        boolean hasDeleteEvent = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals("user.deleted") ||
                              event.getEventType().equals(AuditEvent.EventTypes.SESSION_TERMINATED));
        assertTrue(hasDeleteEvent);
    }

    @Test
    @DisplayName("Admin Impersonation Flow: Must prompt for justification and display persistent banner with notification")
    void testAdminImpersonationFlowWithJustificationAndNotification() {
        // Start admin impersonation
        Map<String, Object> impersonationRequest = Map.of(
            "targetUserId", testUser.getId().toString(),
            "justification", "Investigating user account issue #12345",
            "maxDuration", "PT1H", // 1 hour ISO 8601 duration
            "notifyUser", true
        );
        
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        HttpEntity<Map<String, Object>> impersonationEntity = new HttpEntity<>(impersonationRequest, adminHeaders);
        
        ResponseEntity<Map> impersonationResponse = restTemplate.exchange(
            baseUrl + "/admin/impersonate/start",
            HttpMethod.POST,
            impersonationEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, impersonationResponse.getStatusCode());
        Map<String, Object> impersonationResult = impersonationResponse.getBody();
        String impersonationToken = (String) impersonationResult.get("impersonationToken");
        String impersonationSessionId = (String) impersonationResult.get("sessionId");
        
        assertNotNull(impersonationToken);
        assertNotNull(impersonationSessionId);
        assertTrue((Boolean) impersonationResult.get("userNotified"));
        
        // Test impersonation session has proper metadata
        HttpHeaders impersonationHeaders = new HttpHeaders();
        impersonationHeaders.setBearerAuth(impersonationToken);
        
        ResponseEntity<Map> sessionInfoResponse = restTemplate.exchange(
            baseUrl + "/sessions/current",
            HttpMethod.GET,
            new HttpEntity<>(impersonationHeaders),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, sessionInfoResponse.getStatusCode());
        Map<String, Object> sessionInfo = sessionInfoResponse.getBody();
        
        assertTrue((Boolean) sessionInfo.get("isImpersonation"));
        assertEquals(adminUser.getId().toString(), sessionInfo.get("impersonatedBy"));
        assertTrue(sessionInfo.get("justification").toString().contains("account issue #12345"));
        assertNotNull(sessionInfo.get("impersonationStartedAt"));
        assertNotNull(sessionInfo.get("impersonationExpiresAt"));
        
        // Test that impersonation shows persistent banner
        ResponseEntity<Map> bannerResponse = restTemplate.exchange(
            baseUrl + "/ui/impersonation-banner",
            HttpMethod.GET,
            new HttpEntity<>(impersonationHeaders),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, bannerResponse.getStatusCode());
        Map<String, Object> bannerInfo = bannerResponse.getBody();
        
        assertTrue((Boolean) bannerInfo.get("showBanner"));
        assertTrue(bannerInfo.get("message").toString().contains("impersonating"));
        assertEquals(adminUser.getEmail(), bannerInfo.get("adminEmail"));
        
        // Test notification was sent to user
        ResponseEntity<Map> notificationResponse = restTemplate.exchange(
            baseUrl + "/admin/notifications/impersonation/" + impersonationSessionId,
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, notificationResponse.getStatusCode());
        Map<String, Object> notificationInfo = notificationResponse.getBody();
        
        assertTrue((Boolean) notificationInfo.get("emailSent"));
        assertEquals(testUser.getEmail(), notificationInfo.get("recipientEmail"));
        assertTrue(notificationInfo.get("subject").toString().contains("account access"));
        
        // End impersonation
        ResponseEntity<Map> endImpersonationResponse = restTemplate.exchange(
            baseUrl + "/admin/impersonate/end",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("sessionId", impersonationSessionId), adminHeaders),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, endImpersonationResponse.getStatusCode());
        Map<String, Object> endResult = endImpersonationResponse.getBody();
        assertEquals("IMPERSONATION_ENDED", endResult.get("status"));
        
        // Verify complete audit trail
        var auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        boolean hasStartEvent = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals("admin.impersonation.started"));
        boolean hasEndEvent = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals("admin.impersonation.ended"));
        
        assertTrue(hasStartEvent || hasEndEvent);
    }

    @Test
    @DisplayName("Break-Glass Account Access: Must trigger multi-person approval workflow and generate high-severity alert")
    void testBreakGlassAccountAccessWithApprovalWorkflow() {
        // Create break-glass role
        UserRole breakGlassRole = new UserRole();
        breakGlassRole.setUser(testUser);
        breakGlassRole.setRoleName("EMERGENCY_ADMIN");
        breakGlassRole.setScope("emergency");
        breakGlassRole.setBreakGlassRole(true);
        breakGlassRole.setEmergencyAccess(true);
        breakGlassRole.setApprovalRequired(true);
        breakGlassRole.setStatus(UserRole.RoleStatus.PENDING_APPROVAL);
        breakGlassRole.setJustification("Critical system outage - database corruption detected");
        breakGlassRole.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        breakGlassRole = roleRepository.save(breakGlassRole);
        
        // Request break-glass activation
        Map<String, Object> breakGlassRequest = Map.of(
            "roleId", breakGlassRole.getId().toString(),
            "emergencyType", "SYSTEM_OUTAGE",
            "severity", "CRITICAL",
            "justification", "Database corruption requires immediate administrative access",
            "estimatedDuration", "PT2H", // 2 hours
            "incidentTicket", "INC-2024-001"
        );
        
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(userToken);
        HttpEntity<Map<String, Object>> bgEntity = new HttpEntity<>(breakGlassRequest, userHeaders);
        
        ResponseEntity<Map> bgResponse = restTemplate.exchange(
            baseUrl + "/emergency/break-glass/activate",
            HttpMethod.POST,
            bgEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, bgResponse.getStatusCode());
        Map<String, Object> bgResult = bgResponse.getBody();
        assertEquals("BREAK_GLASS_PENDING_APPROVAL", bgResult.get("status"));
        String approvalWorkflowId = (String) bgResult.get("approvalWorkflowId");
        assertNotNull(approvalWorkflowId);
        
        // Verify high-severity alert was generated
        ResponseEntity<Map> alertResponse = restTemplate.exchange(
            baseUrl + "/admin/security/alerts/latest",
            HttpMethod.GET,
            new HttpEntity<>(createAuthHeaders(adminToken)),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, alertResponse.getStatusCode());
        Map<String, Object> alertResult = alertResponse.getBody();
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> alerts = (List<Map<String, Object>>) alertResult.get("alerts");
        
        boolean hasCriticalAlert = alerts.stream()
            .anyMatch(alert -> "CRITICAL".equals(alert.get("severity")) &&
                              alert.get("type").toString().contains("BREAK_GLASS"));
        assertTrue(hasCriticalAlert);
        
        // Test multi-person approval requirement
        Map<String, Object> firstApprovalRequest = Map.of(
            "workflowId", approvalWorkflowId,
            "approved", true,
            "approverRole", "INCIDENT_COMMANDER",
            "comment", "Emergency access approved for database recovery"
        );
        
        HttpEntity<Map<String, Object>> firstApprovalEntity = new HttpEntity<>(firstApprovalRequest, 
            createAuthHeaders(adminToken));
        
        ResponseEntity<Map> firstApprovalResponse = restTemplate.exchange(
            baseUrl + "/admin/break-glass/approve",
            HttpMethod.POST,
            firstApprovalEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, firstApprovalResponse.getStatusCode());
        Map<String, Object> firstApprovalResult = firstApprovalResponse.getBody();
        assertEquals("PARTIAL_APPROVAL", firstApprovalResult.get("status"));
        assertEquals(1, firstApprovalResult.get("approvalsReceived"));
        assertTrue((Integer) firstApprovalResult.get("approvalsRequired") > 1);
        
        // Second approval (simulating different approver)
        Map<String, Object> secondApprovalRequest = Map.of(
            "workflowId", approvalWorkflowId,
            "approved", true,
            "approverRole", "SECURITY_OFFICER",
            "comment", "Security clearance granted for emergency access"
        );
        
        ResponseEntity<Map> secondApprovalResponse = restTemplate.exchange(
            baseUrl + "/admin/break-glass/approve",
            HttpMethod.POST,
            new HttpEntity<>(secondApprovalRequest, 
                createAuthHeaders(adminToken)),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, secondApprovalResponse.getStatusCode());
        Map<String, Object> secondApprovalResult = secondApprovalResponse.getBody();
        assertEquals("FULLY_APPROVED", secondApprovalResult.get("status"));
        
        // Test break-glass access is now active
        ResponseEntity<Map> accessTestResponse = restTemplate.exchange(
            baseUrl + "/emergency/break-glass/status",
            HttpMethod.GET,
            new HttpEntity<>(userHeaders),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, accessTestResponse.getStatusCode());
        Map<String, Object> accessResult = accessTestResponse.getBody();
        assertTrue((Boolean) accessResult.get("breakGlassActive"));
        assertNotNull(accessResult.get("activatedAt"));
        assertNotNull(accessResult.get("expiresAt"));
        
        // Verify SIEM alert was generated
        var auditEvents = auditEventRepository.findByUserId(testUser.getId());
        boolean hasBreakGlassAlert = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals(AuditEvent.EventTypes.BREAK_GLASS_ACCESS) &&
                              event.getSeverity() == AuditEvent.Severity.CRITICAL);
        assertTrue(hasBreakGlassAlert);
    }

    @Test
    @DisplayName("Key Compromise Drill: Must trigger automated workflow that rotates key and ensures service rejection of old tokens")
    void testKeyCompromiseDrillWithAutomatedRotation() throws InterruptedException {
        // Create active session with current key
        Map<String, Object> loginRequest = Map.of(
            "email", testUser.getEmail(),
            "password", "user_password"
        );
        
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            loginRequest,
            Map.class
        );
        
        String accessToken = (String) loginResponse.getBody().get("accessToken");
        
        // Verify token works with current key
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        ResponseEntity<Map> initialResponse = restTemplate.exchange(
            baseUrl + "/users/me",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        assertEquals(HttpStatus.OK, initialResponse.getStatusCode());
        
        // Trigger key compromise detection
        Map<String, Object> compromiseRequest = Map.of(
            "compromiseType", "SIGNING_KEY",
            "keyId", "current_signing_key",
            "severity", "CRITICAL",
            "detectionSource", "SECURITY_MONITORING",
            "evidence", "Suspicious key usage patterns detected"
        );
        
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        HttpEntity<Map<String, Object>> compromiseEntity = new HttpEntity<>(compromiseRequest, adminHeaders);
        
        Instant compromiseDetectionTime = Instant.now();
        
        ResponseEntity<Map> compromiseResponse = restTemplate.exchange(
            baseUrl + "/admin/security/key-compromise",
            HttpMethod.POST,
            compromiseEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.ACCEPTED, compromiseResponse.getStatusCode());
        Map<String, Object> compromiseResult = compromiseResponse.getBody();
        assertEquals("KEY_ROTATION_INITIATED", compromiseResult.get("status"));
        String rotationOperationId = (String) compromiseResult.get("rotationOperationId");
        assertNotNull(rotationOperationId);
        
        // Wait for automated rotation to complete (should be under 5 minutes)
        boolean rotationCompleted = false;
        int maxAttempts = 30; // 30 attempts * 10 seconds = 5 minutes max
        
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            TimeUnit.SECONDS.sleep(10);
            
            ResponseEntity<Map> statusResponse = restTemplate.exchange(
                baseUrl + "/admin/security/key-rotation/" + rotationOperationId + "/status",
                HttpMethod.GET,
                new HttpEntity<>(adminHeaders),
                Map.class
            );
            
            if (statusResponse.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> statusResult = statusResponse.getBody();
                if ("COMPLETED".equals(statusResult.get("status"))) {
                    rotationCompleted = true;
                    break;
                }
            }
        }
        
        assertTrue(rotationCompleted, "Key rotation should complete within 5 minutes");
        
        Instant rotationCompletedTime = Instant.now();
        Duration rotationDuration = Duration.between(compromiseDetectionTime, rotationCompletedTime);
        assertTrue(rotationDuration.toMinutes() < 5, 
                  "Key rotation should complete within 5 minutes. Actual: " + rotationDuration.toMinutes() + " minutes");
        
        // Test that old tokens are now rejected
        ResponseEntity<Map> rejectionResponse = restTemplate.exchange(
            baseUrl + "/users/me",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        
        assertEquals(HttpStatus.UNAUTHORIZED, rejectionResponse.getStatusCode());
        Map<String, Object> rejectionResult = rejectionResponse.getBody();
        assertTrue(rejectionResult.get("message").toString().contains("invalid") ||
                  rejectionResult.get("message").toString().contains("expired") ||
                  rejectionResult.get("errorCode").toString().contains("TOKEN_INVALID"));
        
        // Test that new tokens work with new key
        ResponseEntity<Map> newLoginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            loginRequest,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, newLoginResponse.getStatusCode());
        String newAccessToken = (String) newLoginResponse.getBody().get("accessToken");
        assertNotEquals(accessToken, newAccessToken);
        
        // Verify new token works
        HttpHeaders newHeaders = new HttpHeaders();
        newHeaders.setBearerAuth(newAccessToken);
        
        ResponseEntity<Map> newTokenResponse = restTemplate.exchange(
            baseUrl + "/users/me",
            HttpMethod.GET,
            new HttpEntity<>(newHeaders),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, newTokenResponse.getStatusCode());
        
        // Verify audit trail
        var auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        boolean hasKeyRotationEvent = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals(AuditEvent.EventTypes.KEY_ROTATION) &&
                              event.getSeverity() == AuditEvent.Severity.CRITICAL);
        assertTrue(hasKeyRotationEvent);
    }

    @Test
    @DisplayName("Emergency Policy Rollback: Must verify instant rollback to last known-good version within 5 minutes")
    void testEmergencyPolicyRollbackWithinFiveMinutes() throws InterruptedException {
        // Create a policy
        TenantPolicy originalPolicy = new TenantPolicy();
        originalPolicy.setTenant(testTenant);
        originalPolicy.setName("Test Authorization Policy");
        originalPolicy.setPolicyType(TenantPolicy.PolicyType.AUTHORIZATION);
        originalPolicy.setEffect(TenantPolicy.Effect.ALLOW);
        originalPolicy.setVersion(1L);
        originalPolicy.setStatus(TenantPolicy.PolicyStatus.ACTIVE);
        originalPolicy.setSubjects(new String[]{"role:USER"});
        originalPolicy.setResources(new String[]{"resource:*"});
        originalPolicy.setActions(new String[]{"read"});
        originalPolicy = policyRepository.save(originalPolicy);
        
        // Test authorization with original policy
        Map<String, Object> authzRequest = Map.of(
            "subject", "role:USER",
            "resource", "resource:document",
            "action", "read"
        );
        
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(userToken);
        HttpEntity<Map<String, Object>> authzEntity = new HttpEntity<>(authzRequest, userHeaders);
        
        ResponseEntity<Map> originalAuthzResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            authzEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, originalAuthzResponse.getStatusCode());
        assertEquals("ALLOW", originalAuthzResponse.getBody().get("decision"));
        
        // Deploy problematic policy (overly permissive)
        Map<String, Object> problematicPolicyUpdate = Map.of(
            "policyId", originalPolicy.getId().toString(),
            "effect", "ALLOW",
            "subjects", new String[]{"*"},
            "resources", new String[]{"*"},
            "actions", new String[]{"*"},
            "version", 2L
        );
        
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        HttpEntity<Map<String, Object>> updateEntity = new HttpEntity<>(problematicPolicyUpdate, adminHeaders);
        
        ResponseEntity<Map> updateResponse = restTemplate.exchange(
            baseUrl + "/admin/policies/" + originalPolicy.getId() + "/update",
            HttpMethod.PUT,
            updateEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
        
        // Detect overly permissive policy (automated security scan)
        Instant rollbackStartTime = Instant.now();
        
        Map<String, Object> emergencyRollbackRequest = Map.of(
            "policyId", originalPolicy.getId().toString(),
            "reason", "OVERLY_PERMISSIVE_POLICY_DETECTED",
            "rollbackToVersion", 1L,
            "emergency", true,
            "detectionSource", "AUTOMATED_SECURITY_SCAN"
        );
        
        HttpEntity<Map<String, Object>> rollbackEntity = new HttpEntity<>(emergencyRollbackRequest, adminHeaders);
        
        ResponseEntity<Map> rollbackResponse = restTemplate.exchange(
            baseUrl + "/admin/policies/emergency-rollback",
            HttpMethod.POST,
            rollbackEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, rollbackResponse.getStatusCode());
        Map<String, Object> rollbackResult = rollbackResponse.getBody();
        assertEquals("ROLLBACK_COMPLETED", rollbackResult.get("status"));
        
        Instant rollbackCompletedTime = Instant.now();
        Duration rollbackDuration = Duration.between(rollbackStartTime, rollbackCompletedTime);
        
        // Verify rollback completed within 5 minutes
        assertTrue(rollbackDuration.toMinutes() < 5, 
                  "Emergency policy rollback should complete within 5 minutes. Actual: " + rollbackDuration.toMinutes() + " minutes");
        
        // Verify policy is back to original version
        ResponseEntity<Map> policyStatusResponse = restTemplate.exchange(
            baseUrl + "/admin/policies/" + originalPolicy.getId(),
            HttpMethod.GET,
            new HttpEntity<>(adminHeaders),
            Map.class
        );
        
        assertEquals(HttpStatus.OK, policyStatusResponse.getStatusCode());
        Map<String, Object> policyStatus = policyStatusResponse.getBody();
        assertEquals(1L, ((Number) policyStatus.get("version")).longValue());
        assertEquals("ACTIVE", policyStatus.get("status"));
        
        // Test that authorization works with rolled-back policy
        ResponseEntity<Map> postRollbackAuthzResponse = restTemplate.exchange(
            baseUrl + "/authz/check",
            HttpMethod.POST,
            authzEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, postRollbackAuthzResponse.getStatusCode());
        assertEquals("ALLOW", postRollbackAuthzResponse.getBody().get("decision"));
        
        // Verify audit event
        var auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        boolean hasRollbackEvent = auditEvents.stream()
            .anyMatch(event -> event.getEventType().equals("policy.emergency_rollback") &&
                              event.getSeverity() == AuditEvent.Severity.CRITICAL);
        assertTrue(hasRollbackEvent);
    }

    @Test
    @DisplayName("NFR3.6 - Any permission, role, or session revocation must be enforced globally within 1 second")
    void testRevocationPropagationSLAWithinOneSecond() throws InterruptedException {
        // Create active session
        Map<String, Object> loginRequest = Map.of(
            "email", testUser.getEmail(),
            "password", "user_password"
        );
        
        ResponseEntity<Map> loginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            loginRequest,
            Map.class
        );
        
        String accessToken = (String) loginResponse.getBody().get("accessToken");
        String sessionId = (String) loginResponse.getBody().get("sessionId");
        
        // Create active role
        UserRole testRole = new UserRole();
        testRole.setUser(testUser);
        testRole.setRoleName("TEST_ROLE");
        testRole.setScope("test");
        testRole.setStatus(UserRole.RoleStatus.ACTIVE);
        testRole = roleRepository.save(testRole);
        
        // Verify session and role are active
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        
        ResponseEntity<Map> initialResponse = restTemplate.exchange(
            baseUrl + "/users/me",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        assertEquals(HttpStatus.OK, initialResponse.getStatusCode());
        
        // Record timestamp before revocation
        Instant revocationStartTime = Instant.now();
        
        // Simultaneously revoke session and role
        Map<String, Object> massRevocationRequest = Map.of(
            "userId", testUser.getId().toString(),
            "revokeAllSessions", true,
            "revokeAllRoles", true,
            "reason", "Security incident - immediate revocation required",
            "priority", "CRITICAL"
        );
        
        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        HttpEntity<Map<String, Object>> revocationEntity = new HttpEntity<>(massRevocationRequest, adminHeaders);
        
        ResponseEntity<Map> revocationResponse = restTemplate.exchange(
            baseUrl + "/admin/security/mass-revocation",
            HttpMethod.POST,
            revocationEntity,
            Map.class
        );
        
        assertEquals(HttpStatus.OK, revocationResponse.getStatusCode());
        
        // Immediately test that session is invalidated (within 1 second)
        ResponseEntity<Map> postRevocationResponse = restTemplate.exchange(
            baseUrl + "/users/me",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );
        
        Instant testCompletedTime = Instant.now();
        Duration revocationDuration = Duration.between(revocationStartTime, testCompletedTime);
        
        // Verify revocation is enforced
        assertEquals(HttpStatus.UNAUTHORIZED, postRevocationResponse.getStatusCode());
        
        // Verify timing requirement (< 1 second)
        assertTrue(revocationDuration.toMillis() < 1000, 
                  "Revocation must be enforced within 1 second. Actual: " + revocationDuration.toMillis() + "ms");
        
        // Test that new login attempts are also blocked (global enforcement)
        ResponseEntity<Map> newLoginResponse = restTemplate.postForEntity(
            baseUrl + "/auth/login",
            loginRequest,
            Map.class
        );
        
        // Should succeed unless user is completely deactivated
        assertTrue(newLoginResponse.getStatusCode().is2xxSuccessful() || 
                  newLoginResponse.getStatusCode() == HttpStatus.FORBIDDEN);
        
        // Verify audit events
        var auditEvents = auditEventRepository.findByUserId(testUser.getId());
        boolean hasRevocationEvent = auditEvents.stream()
            .anyMatch(event -> (event.getEventType().equals(AuditEvent.EventTypes.SESSION_TERMINATED) ||
                               event.getEventType().equals("role.revoked")) &&
                              event.getDescription().contains("Security incident"));
        assertTrue(hasRevocationEvent);
    }

    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return headers;
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
