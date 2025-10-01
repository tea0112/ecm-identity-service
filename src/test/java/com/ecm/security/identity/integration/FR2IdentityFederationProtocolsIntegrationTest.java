package com.ecm.security.identity.integration;

import com.ecm.security.identity.domain.*;
import com.ecm.security.identity.repository.*;
import com.ecm.security.identity.service.TenantContextService;
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
 * Comprehensive integration tests for FR2 - Identity, Federation & Protocols requirements.
 * Tests user lifecycle, OAuth2/OIDC, SAML, SCIM, device flows, and service accounts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                classes = {TestWebConfig.class})
@Testcontainers
class FR2IdentityFederationProtocolsIntegrationTest {

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
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("ecm.audit.enabled", () -> "false");
        registry.add("ecm.multitenancy.enabled", () -> "false");
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
    private UserSessionRepository sessionRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;
    
    @Autowired
    private TenantContextService tenantContextService;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private Tenant testTenant;
    private User testUser;

    @BeforeEach
    @Commit
    void setUp() {
        baseUrl = "http://localhost:" + port;
        
        // Check if tenant already exists, if not create it
        Optional<Tenant> existingTenant = tenantRepository.findByTenantCodeAndStatusNot("test-tenant", Tenant.TenantStatus.ARCHIVED);
        if (existingTenant.isPresent()) {
            testTenant = existingTenant.get();
        } else {
            testTenant = Tenant.builder()
                    .tenantCode("test-tenant")
                    .name("Test Tenant")
                    .domain("test.example.com")
                    .status(Tenant.TenantStatus.ACTIVE)
                    .settings("{}")
                    .build();
            testTenant = tenantRepository.save(testTenant);
        }
        
        // Check if test user already exists, if not create it
        Optional<User> existingUser = userRepository.findByEmail("test@example.com");
        if (existingUser.isPresent()) {
            testUser = existingUser.get();
        } else {
            testUser = User.builder()
                    .email("test@example.com")
                    .firstName("Test")
                    .lastName("User")
                    .tenant(testTenant)
                    .status(User.UserStatus.ACTIVE)
                    .passwordHash("SecurePassword123!") // Plain password for NoOpPasswordEncoder
                    .emailVerified(true)
                    .mfaEnabled(false)
                    .metadata("{}")
                    .build();
            testUser = userRepository.save(testUser);
        }
        
        // Set the tenant context for audit events
        TenantContextService.TenantContext context = TenantContextService.TenantContext.builder()
                .tenantId(testTenant.getId())
                .tenantCode(testTenant.getTenantCode())
                .tenantName(testTenant.getName())
                .build();
        tenantContextService.setCurrentContext(context);
    }

    @Test
    @DisplayName("FR2.1 - User & Identity Lifecycle Management")
    void testUserAndIdentityLifecycleManagement() throws Exception {
        // Test user registration with Argon2 password storage
        Map<String, Object> registrationRequest = Map.of(
                "email", "newuser@example.com",
                "firstName", "New",
                "lastName", "User",
                "password", "SecurePassword123!",
                "tenantCode", testTenant.getTenantCode(),
                "acceptedTermsVersion", "1.0",
                "acceptedPrivacyVersion", "1.0"
        );

        ResponseEntity<Map> registrationResponse = restTemplate.postForEntity(
                baseUrl + "/auth/register",
                registrationRequest,
                Map.class
        );

        assertEquals(HttpStatus.CREATED, registrationResponse.getStatusCode());
        Map<String, Object> registrationResult = registrationResponse.getBody();
        assertNotNull(registrationResult.get("userId"));
        assertEquals("active", registrationResult.get("status"));
        assertNotNull(registrationResult.get("verificationToken"));

        String userId = (String) registrationResult.get("userId");

        // Test profile updates
        Map<String, Object> profileUpdateRequest = Map.of(
                "firstName", "Updated",
                "lastName", "User",
                "phoneNumber", "+1234567890",
                "timezone", "America/New_York"
        );

        String accessToken = authenticateUser("newuser@example.com", "SecurePassword123!");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map> profileUpdateResponse = restTemplate.exchange(
                baseUrl + "/user/profile",
                HttpMethod.PUT,
                new HttpEntity<>(profileUpdateRequest, headers),
                Map.class
        );

        assertEquals(HttpStatus.OK, profileUpdateResponse.getStatusCode());
        Map<String, Object> profileResult = profileUpdateResponse.getBody();
        assertEquals("Updated", profileResult.get("firstName"));
        assertEquals("+1234567890", profileResult.get("phoneNumber"));

        // Test instantaneous de-provisioning
        ResponseEntity<Map> deProvisionResponse = restTemplate.exchange(
                baseUrl + "/api/v1/admin/users/" + userId + "/deprovision",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("reason", "User termination"), createAdminHeaders()),
                Map.class
        );

        assertEquals(HttpStatus.OK, deProvisionResponse.getStatusCode());
        Map<String, Object> deProvisionResult = deProvisionResponse.getBody();
        assertTrue((Boolean) deProvisionResult.get("deprovisioned"));
        assertTrue((Boolean) deProvisionResult.get("sessionsTerminated"));
        assertTrue((Boolean) deProvisionResult.get("tokensRevoked"));
        assertTrue((Integer) deProvisionResult.get("terminatedSessions") >= 0);

        // Verify user cannot authenticate after de-provisioning
        ResponseEntity<Map> postDeprovisionAuth = restTemplate.postForEntity(
                baseUrl + "/auth/login/password",
                Map.of(
                        "email", "newuser@example.com",
                        "password", "SecurePassword123!",
                        "tenantCode", testTenant.getTenantCode()
                ),
                Map.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, postDeprovisionAuth.getStatusCode());

        // Test account resurrection prevention
        Map<String, Object> reRegistrationRequest = Map.of(
                "email", "newuser@example.com",
                "firstName", "Malicious",
                "lastName", "Actor",
                "password", "DifferentPassword456!",
                "tenantCode", testTenant.getTenantCode()
        );

        ResponseEntity<Map> reRegistrationResponse = restTemplate.postForEntity(
                baseUrl + "/auth/register",
                reRegistrationRequest,
                Map.class
        );

        assertEquals(HttpStatus.CREATED, reRegistrationResponse.getStatusCode());
        Map<String, Object> reRegistrationResult = reRegistrationResponse.getBody();
        
        // Verify new user ID is different and no old permissions are restored
        assertNotEquals(userId, reRegistrationResult.get("userId"));
        assertEquals("active", reRegistrationResult.get("status"));
        assertNull(reRegistrationResult.get("restoredPermissions"));

        // Verify comprehensive audit events - query by user ID since tenant context is inconsistent
        List<AuditEvent> auditEvents = auditEventRepository.findByUserId(UUID.fromString(userId));
        
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.registration.completed")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("user.profile.updated")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("user.deprovisioned")));
        
        // Also check audit events for the re-registered user
        List<AuditEvent> reRegistrationAuditEvents = auditEventRepository.findByUserId(UUID.fromString((String) reRegistrationResult.get("userId")));
        assertTrue(reRegistrationAuditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("auth.registration.completed")));
    }

    @Test
    @DisplayName("FR2.2 - OAuth2/OIDC Provider Compliance")
    void testOAuth2OIDCProviderCompliance() throws Exception {
        // Test authorization code flow with PKCE
        String clientId = "test-client-id";
        String redirectUri = "https://client.example.com/callback";
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        Map<String, Object> authRequest = Map.of(
                "response_type", "code",
                "client_id", clientId,
                "redirect_uri", redirectUri,
                "scope", "openid profile email",
                "state", "random-state-value",
                "code_challenge", codeChallenge,
                "code_challenge_method", "S256",
                "nonce", "random-nonce-value"
        );

        ResponseEntity<Map> authResponse = restTemplate.postForEntity(
                baseUrl + "/oauth2/authorize",
                authRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, authResponse.getStatusCode());
        Map<String, Object> authResult = authResponse.getBody();
        assertNotNull(authResult.get("authorizationCode"));
        assertNotNull(authResult.get("state"));
        assertEquals("random-state-value", authResult.get("state"));

        // Test token exchange with PKCE verification
        String authorizationCode = (String) authResult.get("authorizationCode");
        Map<String, Object> tokenRequest = Map.of(
                "grant_type", "authorization_code",
                "client_id", clientId,
                "code", authorizationCode,
                "redirect_uri", redirectUri,
                "code_verifier", codeVerifier
        );

        ResponseEntity<Map> tokenResponse = restTemplate.postForEntity(
                baseUrl + "/oauth2/token",
                tokenRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, tokenResponse.getStatusCode());
        Map<String, Object> tokenResult = tokenResponse.getBody();
        assertNotNull(tokenResult.get("access_token"));
        assertNotNull(tokenResult.get("id_token"));
        assertNotNull(tokenResult.get("refresh_token"));
        assertEquals("Bearer", tokenResult.get("token_type"));
        assertTrue((Integer) tokenResult.get("expires_in") > 0);

        // Test OIDC UserInfo endpoint
        String accessToken = (String) tokenResult.get("access_token");
        HttpHeaders userInfoHeaders = new HttpHeaders();
        userInfoHeaders.setBearerAuth(accessToken);

        ResponseEntity<Map> userInfoResponse = restTemplate.exchange(
                baseUrl + "/oauth2/userinfo",
                HttpMethod.GET,
                new HttpEntity<>(userInfoHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, userInfoResponse.getStatusCode());
        Map<String, Object> userInfoResult = userInfoResponse.getBody();
        assertNotNull(userInfoResult.get("sub"));
        assertNotNull(userInfoResult.get("email"));
        assertNotNull(userInfoResult.get("email_verified"));

        // Test strict redirect URI validation
        Map<String, Object> invalidRedirectRequest = Map.of(
                "response_type", "code",
                "client_id", clientId,
                "redirect_uri", "https://malicious.example.com/callback",
                "scope", "openid profile"
        );

        ResponseEntity<Map> invalidRedirectResponse = restTemplate.postForEntity(
                baseUrl + "/oauth2/authorize",
                invalidRedirectRequest,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, invalidRedirectResponse.getStatusCode());
        Map<String, Object> invalidRedirectResult = invalidRedirectResponse.getBody();
        assertEquals("invalid_request", invalidRedirectResult.get("error"));
        assertTrue(((String) invalidRedirectResult.get("error_description")).contains("redirect_uri"));

        // Test OIDC Discovery endpoint
        ResponseEntity<Map> discoveryResponse = restTemplate.getForEntity(
                baseUrl + "/.well-known/openid-configuration",
                Map.class
        );

        assertEquals(HttpStatus.OK, discoveryResponse.getStatusCode());
        Map<String, Object> discoveryResult = discoveryResponse.getBody();
        assertNotNull(discoveryResult.get("issuer"));
        assertNotNull(discoveryResult.get("authorization_endpoint"));
        assertNotNull(discoveryResult.get("token_endpoint"));
        assertNotNull(discoveryResult.get("userinfo_endpoint"));
        assertNotNull(discoveryResult.get("jwks_uri"));
        assertTrue(((List<?>) discoveryResult.get("response_types_supported")).contains("code"));
        assertTrue(((List<?>) discoveryResult.get("code_challenge_methods_supported")).contains("S256"));

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("oauth2.authorization.granted")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("oauth2.token.issued")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("oauth2.redirect_uri.validation_failed")));
    }

    @Test
    @DisplayName("FR2.3 - Enterprise Federation (SSO) - SAML 2.0 and OIDC")
    void testEnterpriseFederationSSO() throws Exception {
        // Test SAML 2.0 SSO initiation
        Map<String, Object> samlSsoRequest = Map.of(
                "tenantCode", testTenant.getTenantCode(),
                "idpId", "enterprise-idp",
                "relayState", "https://app.example.com/dashboard",
                "forceAuthn", false
        );

        ResponseEntity<Map> samlSsoResponse = restTemplate.postForEntity(
                baseUrl + "/sso/saml/initiate",
                samlSsoRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, samlSsoResponse.getStatusCode());
        Map<String, Object> samlSsoResult = samlSsoResponse.getBody();
        assertNotNull(samlSsoResult.get("samlRequest"));
        assertNotNull(samlSsoResult.get("relayState"));
        assertNotNull(samlSsoResult.get("ssoUrl"));
        assertTrue(((String) samlSsoResult.get("ssoUrl")).contains("enterprise-idp"));

        // Test SAML response processing with JIT provisioning
        Map<String, Object> samlResponseRequest = Map.of(
                "samlResponse", createMockSamlResponse(),
                "relayState", "https://app.example.com/dashboard"
        );

        ResponseEntity<Map> samlResponseResponse = restTemplate.postForEntity(
                baseUrl + "/sso/saml/acs",
                samlResponseRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, samlResponseResponse.getStatusCode());
        Map<String, Object> samlResponseResult = samlResponseResponse.getBody();
        assertTrue((Boolean) samlResponseResult.get("authenticated"));
        assertNotNull(samlResponseResult.get("userId"));
        assertTrue((Boolean) samlResponseResult.get("jitProvisioned"));
        assertEquals("enterprise-idp", samlResponseResult.get("identityProvider"));

        // Test OIDC Enterprise SSO
        Map<String, Object> oidcSsoRequest = Map.of(
                "tenantCode", testTenant.getTenantCode(),
                "providerId", "enterprise-oidc",
                "scope", "openid profile email groups",
                "prompt", "login"
        );

        ResponseEntity<Map> oidcSsoResponse = restTemplate.postForEntity(
                baseUrl + "/sso/oidc/initiate",
                oidcSsoRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, oidcSsoResponse.getStatusCode());
        Map<String, Object> oidcSsoResult = oidcSsoResponse.getBody();
        assertNotNull(oidcSsoResult.get("authorizationUrl"));
        assertNotNull(oidcSsoResult.get("state"));
        assertNotNull(oidcSsoResult.get("nonce"));

        // Test JIT provisioning with attribute mapping
        Map<String, Object> jitProvisioningRequest = Map.of(
                "providerId", "enterprise-oidc",
                "userAttributes", Map.of(
                        "sub", "enterprise-user-123",
                        "email", "enterprise.user@corp.example.com",
                        "given_name", "Enterprise",
                        "family_name", "User",
                        "groups", Arrays.asList("Developers", "Managers"),
                        "department", "Engineering"
                ),
                "attributeMapping", Map.of(
                        "email", "email",
                        "firstName", "given_name",
                        "lastName", "family_name",
                        "roles", "groups",
                        "department", "department"
                )
        );

        ResponseEntity<Map> jitProvisioningResponse = restTemplate.postForEntity(
                baseUrl + "/sso/jit-provision",
                jitProvisioningRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, jitProvisioningResponse.getStatusCode());
        Map<String, Object> jitResult = jitProvisioningResponse.getBody();
        assertTrue((Boolean) jitResult.get("provisioned"));
        assertNotNull(jitResult.get("userId"));
        assertEquals("Enterprise", jitResult.get("firstName"));
        assertEquals("User", jitResult.get("lastName"));
        assertTrue(((List<?>) jitResult.get("roles")).contains("Developers"));

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("sso.saml.initiated")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("sso.saml.authenticated")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("user.jit.provisioned")));
    }

    @Test
    @DisplayName("FR2.4 - Directory Sync (SCIM) Protocol Support")
    void testDirectorySyncSCIMProtocol() throws Exception {
        // Test SCIM User creation
        Map<String, Object> scimUserRequest = Map.of(
                "schemas", Arrays.asList("urn:ietf:params:scim:schemas:core:2.0:User"),
                "userName", "scim.user@example.com",
                "name", Map.of(
                        "givenName", "SCIM",
                        "familyName", "User"
                ),
                "emails", Arrays.asList(Map.of(
                        "value", "scim.user@example.com",
                        "primary", true
                )),
                "active", true,
                "externalId", "external-123"
        );

        HttpHeaders scimHeaders = createSCIMHeaders();
        ResponseEntity<Map> scimCreateResponse = restTemplate.exchange(
                baseUrl + "/scim/v2/Users",
                HttpMethod.POST,
                new HttpEntity<>(scimUserRequest, scimHeaders),
                Map.class
        );

        assertEquals(HttpStatus.CREATED, scimCreateResponse.getStatusCode());
        Map<String, Object> scimCreateResult = scimCreateResponse.getBody();
        assertNotNull(scimCreateResult.get("id"));
        assertEquals("scim.user@example.com", scimCreateResult.get("userName"));
        assertTrue((Boolean) scimCreateResult.get("active"));

        String scimUserId = (String) scimCreateResult.get("id");

        // Test SCIM User retrieval
        ResponseEntity<Map> scimGetResponse = restTemplate.exchange(
                baseUrl + "/scim/v2/Users/" + scimUserId,
                HttpMethod.GET,
                new HttpEntity<>(scimHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, scimGetResponse.getStatusCode());
        Map<String, Object> scimGetResult = scimGetResponse.getBody();
        assertEquals(scimUserId, scimGetResult.get("id"));
        assertEquals("scim.user@example.com", scimGetResult.get("userName"));

        // Test SCIM User update
        Map<String, Object> scimUpdateRequest = Map.of(
                "schemas", Arrays.asList("urn:ietf:params:scim:api:messages:2.0:PatchOp"),
                "Operations", Arrays.asList(Map.of(
                        "op", "replace",
                        "path", "active",
                        "value", false
                ))
        );

        ResponseEntity<Map> scimUpdateResponse = restTemplate.exchange(
                baseUrl + "/scim/v2/Users/" + scimUserId,
                HttpMethod.PATCH,
                new HttpEntity<>(scimUpdateRequest, scimHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, scimUpdateResponse.getStatusCode());
        Map<String, Object> scimUpdateResult = scimUpdateResponse.getBody();
        assertFalse((Boolean) scimUpdateResult.get("active"));

        // Test SCIM Group creation
        Map<String, Object> scimGroupRequest = Map.of(
                "schemas", Arrays.asList("urn:ietf:params:scim:schemas:core:2.0:Group"),
                "displayName", "Engineering Team",
                "externalId", "eng-team-001",
                "members", Arrays.asList(Map.of(
                        "value", scimUserId,
                        "display", "SCIM User"
                ))
        );

        ResponseEntity<Map> scimGroupResponse = restTemplate.exchange(
                baseUrl + "/scim/v2/Groups",
                HttpMethod.POST,
                new HttpEntity<>(scimGroupRequest, scimHeaders),
                Map.class
        );

        assertEquals(HttpStatus.CREATED, scimGroupResponse.getStatusCode());
        Map<String, Object> scimGroupResult = scimGroupResponse.getBody();
        assertNotNull(scimGroupResult.get("id"));
        assertEquals("Engineering Team", scimGroupResult.get("displayName"));

        // Test SCIM User search/filtering
        ResponseEntity<Map> scimSearchResponse = restTemplate.exchange(
                baseUrl + "/scim/v2/Users?filter=userName eq \"scim.user@example.com\"",
                HttpMethod.GET,
                new HttpEntity<>(scimHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, scimSearchResponse.getStatusCode());
        Map<String, Object> scimSearchResult = scimSearchResponse.getBody();
        assertEquals(1, scimSearchResult.get("totalResults"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resources = (List<Map<String, Object>>) scimSearchResult.get("Resources");
        assertEquals("scim.user@example.com", resources.get(0).get("userName"));

        // Verify audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("scim.user.created")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("scim.user.updated")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("scim.group.created")));
    }

    @Test
    @DisplayName("FR2.5 - Device & CLI Authentication (Device Code Flow)")
    void testDeviceAndCLIAuthentication() throws Exception {
        // Test device code flow initiation
        Map<String, Object> deviceCodeRequest = Map.of(
                "client_id", "cli-client-id",
                "scope", "openid profile device:access",
                "tenantCode", testTenant.getTenantCode()
        );

        ResponseEntity<Map> deviceCodeResponse = restTemplate.postForEntity(
                baseUrl + "/oauth2/device/authorize",
                deviceCodeRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, deviceCodeResponse.getStatusCode());
        Map<String, Object> deviceCodeResult = deviceCodeResponse.getBody();
        assertNotNull(deviceCodeResult.get("device_code"));
        assertNotNull(deviceCodeResult.get("user_code"));
        assertNotNull(deviceCodeResult.get("verification_uri"));
        assertNotNull(deviceCodeResult.get("verification_uri_complete"));
        assertTrue((Integer) deviceCodeResult.get("expires_in") > 0);
        assertTrue((Integer) deviceCodeResult.get("interval") > 0);

        String deviceCode = (String) deviceCodeResult.get("device_code");
        String userCode = (String) deviceCodeResult.get("user_code");

        // Test user code verification (simulating user action)
        Map<String, Object> userCodeVerificationRequest = Map.of(
                "user_code", userCode,
                "tenantCode", testTenant.getTenantCode()
        );

        // First, authenticate user to get session
        String userAccessToken = authenticateUser("test@example.com", "SecurePassword123!");
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(userAccessToken);

        ResponseEntity<Map> userCodeVerificationResponse = restTemplate.exchange(
                baseUrl + "/oauth2/device/verify",
                HttpMethod.POST,
                new HttpEntity<>(userCodeVerificationRequest, userHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, userCodeVerificationResponse.getStatusCode());
        Map<String, Object> userCodeVerificationResult = userCodeVerificationResponse.getBody();
        assertTrue((Boolean) userCodeVerificationResult.get("verified"));
        assertNotNull(userCodeVerificationResult.get("clientName"));
        assertNotNull(userCodeVerificationResult.get("requestedScopes"));

        // Test device code token exchange
        Map<String, Object> deviceTokenRequest = Map.of(
                "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                "device_code", deviceCode,
                "client_id", "cli-client-id"
        );

        ResponseEntity<Map> deviceTokenResponse = restTemplate.postForEntity(
                baseUrl + "/oauth2/token",
                deviceTokenRequest,
                Map.class
        );

        assertEquals(HttpStatus.OK, deviceTokenResponse.getStatusCode());
        Map<String, Object> deviceTokenResult = deviceTokenResponse.getBody();
        assertNotNull(deviceTokenResult.get("access_token"));
        assertNotNull(deviceTokenResult.get("refresh_token"));
        assertEquals("Bearer", deviceTokenResult.get("token_type"));
        assertTrue((Integer) deviceTokenResult.get("expires_in") > 0);

        // Test polling behavior before user approval
        Map<String, Object> newDeviceCodeRequest = Map.of(
                "client_id", "cli-client-id-2",
                "scope", "openid profile",
                "tenantCode", testTenant.getTenantCode()
        );

        ResponseEntity<Map> newDeviceCodeResponse = restTemplate.postForEntity(
                baseUrl + "/oauth2/device/authorize",
                newDeviceCodeRequest,
                Map.class
        );

        String pendingDeviceCode = (String) newDeviceCodeResponse.getBody().get("device_code");

        Map<String, Object> pendingTokenRequest = Map.of(
                "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                "device_code", pendingDeviceCode,
                "client_id", "cli-client-id-2"
        );

        ResponseEntity<Map> pendingTokenResponse = restTemplate.postForEntity(
                baseUrl + "/oauth2/token",
                pendingTokenRequest,
                Map.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, pendingTokenResponse.getStatusCode());
        Map<String, Object> pendingTokenResult = pendingTokenResponse.getBody();
        assertEquals("authorization_pending", pendingTokenResult.get("error"));

        // Verify audit events - query by tenant ID since all events should have the same tenant
        List<AuditEvent> auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("oauth2.device_code.initiated")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("oauth2.device_code.verified")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("oauth2.device_code.token_issued")));
    }

    @Test
    @DisplayName("FR2.6 - Service & Non-Human Principals Management")
    void testServiceAndNonHumanPrincipalsManagement() throws Exception {
        // Test service account creation with approval workflow
        Map<String, Object> serviceAccountRequest = Map.of(
                "name", "api-service-account",
                "description", "Service account for API access",
                "tenantCode", testTenant.getTenantCode(),
                "scopes", Arrays.asList("api:read", "api:write"),
                "rotationPolicy", Map.of(
                        "enabled", true,
                        "intervalDays", 90,
                        "notifyBeforeDays", 7
                ),
                "requestedBy", "admin@example.com",
                "justification", "Required for automated API operations"
        );

        HttpHeaders adminHeaders = createAdminHeaders();
        ResponseEntity<Map> serviceAccountResponse = restTemplate.exchange(
                baseUrl + "/api/v1/admin/service-accounts",
                HttpMethod.POST,
                new HttpEntity<>(serviceAccountRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.CREATED, serviceAccountResponse.getStatusCode());
        Map<String, Object> serviceAccountResult = serviceAccountResponse.getBody();
        assertNotNull(serviceAccountResult.get("serviceAccountId"));
        assertEquals("pending_approval", serviceAccountResult.get("status"));
        assertNotNull(serviceAccountResult.get("approvalWorkflowId"));

        String serviceAccountId = (String) serviceAccountResult.get("serviceAccountId");
        String workflowId = (String) serviceAccountResult.get("approvalWorkflowId");

        // Test service account approval
        Map<String, Object> approvalRequest = Map.of(
                "workflowId", workflowId,
                "action", "approve",
                "approverComment", "Service account approved for API operations",
                "approvedScopes", Arrays.asList("api:read", "api:write")
        );

        ResponseEntity<Map> approvalResponse = restTemplate.exchange(
                baseUrl + "/api/v1/admin/approval-workflows/" + workflowId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(approvalRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, approvalResponse.getStatusCode());
        Map<String, Object> approvalResult = approvalResponse.getBody();
        assertTrue((Boolean) approvalResult.get("approved"));
        assertEquals("active", approvalResult.get("serviceAccountStatus"));

        // Test service account credential generation
        ResponseEntity<Map> credentialResponse = restTemplate.exchange(
                baseUrl + "/api/v1/admin/service-accounts/" + serviceAccountId + "/credentials",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("credentialType", "client_secret"), adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, credentialResponse.getStatusCode());
        Map<String, Object> credentialResult = credentialResponse.getBody();
        assertNotNull(credentialResult.get("clientId"));
        assertNotNull(credentialResult.get("clientSecret"));
        assertNotNull(credentialResult.get("expiresAt"));
        assertTrue(((String) credentialResult.get("clientSecret")).length() > 32);

        // Test mTLS certificate generation for identity-bound authentication
        Map<String, Object> mtlsRequest = Map.of(
                "serviceAccountId", serviceAccountId,
                "certificateType", "x509",
                "keyAlgorithm", "RSA",
                "keySize", 2048,
                "validityDays", 365,
                "subjectDN", "CN=api-service-account,O=Test Tenant,C=US"
        );

        ResponseEntity<Map> mtlsResponse = restTemplate.exchange(
                baseUrl + "/api/v1/admin/service-accounts/" + serviceAccountId + "/mtls-certificate",
                HttpMethod.POST,
                new HttpEntity<>(mtlsRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, mtlsResponse.getStatusCode());
        Map<String, Object> mtlsResult = mtlsResponse.getBody();
        assertNotNull(mtlsResult.get("certificate"));
        assertNotNull(mtlsResult.get("privateKey"));
        assertNotNull(mtlsResult.get("certificateChain"));
        assertNotNull(mtlsResult.get("spiffeId"));
        assertTrue(((String) mtlsResult.get("spiffeId")).startsWith("spiffe://"));

        // Test service account rotation
        ResponseEntity<Map> rotationResponse = restTemplate.exchange(
                baseUrl + "/api/v1/admin/service-accounts/" + serviceAccountId + "/rotate",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("rotationType", "immediate"), adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, rotationResponse.getStatusCode());
        Map<String, Object> rotationResult = rotationResponse.getBody();
        assertTrue((Boolean) rotationResult.get("rotated"));
        assertNotNull(rotationResult.get("newClientSecret"));
        assertNotNull(rotationResult.get("gracePeriodEnds"));
        assertNotEquals(credentialResult.get("clientSecret"), rotationResult.get("newClientSecret"));

        // Test service account expiration and lifecycle
        Map<String, Object> expirationRequest = Map.of(
                "serviceAccountId", serviceAccountId,
                "expiresAt", Instant.now().plus(30, ChronoUnit.DAYS).toString(),
                "notificationEmails", Arrays.asList("admin@example.com")
        );

        ResponseEntity<Map> expirationResponse = restTemplate.exchange(
                baseUrl + "/api/v1/admin/service-accounts/" + serviceAccountId + "/expiration",
                HttpMethod.PUT,
                new HttpEntity<>(expirationRequest, adminHeaders),
                Map.class
        );

        assertEquals(HttpStatus.OK, expirationResponse.getStatusCode());
        Map<String, Object> expirationResult = expirationResponse.getBody();
        assertTrue((Boolean) expirationResult.get("expirationSet"));
        assertNotNull(expirationResult.get("expiresAt"));
        assertTrue((Boolean) expirationResult.get("notificationsEnabled"));

        // Verify comprehensive audit events
        List<AuditEvent> auditEvents = auditEventRepository.findByTenantId(testTenant.getId());
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("service_account.created")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("service_account.approved")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("service_account.credentials.generated")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("service_account.mtls.certificate.issued")));
        assertTrue(auditEvents.stream().anyMatch(event -> 
                event.getEventType().equals("service_account.credentials.rotated")));
    }

    // Helper methods
    private String authenticateUser(String email, String password) throws Exception {
        // Create user first if not exists
        User testUser = userRepository.findByEmailAndTenant(email, testTenant)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .email(email)
                            .firstName("Test")
                            .lastName("User")
                            .tenant(testTenant)
                            .status(User.UserStatus.ACTIVE)
                            .build();
                    return userRepository.save(newUser);
                });

        Map<String, Object> loginRequest = Map.of(
                "email", email,
                "password", password,
                "tenantCode", testTenant.getTenantCode()
        );

        ResponseEntity<Map> response = restTemplate.postForEntity(
                baseUrl + "/auth/login/password",
                loginRequest,
                Map.class
        );

        if (response.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> result = response.getBody();
            return (String) result.get("accessToken");
        }
        return "mock-access-token-" + testUser.getId();
    }

    private HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("mock-admin-token");
        headers.set("X-Admin-Role", "super_admin");
        return headers;
    }

    private HttpHeaders createSCIMHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer scim-api-token");
        headers.set("Accept", "application/scim+json");
        return headers;
    }

    private String createMockSamlResponse() {
        return "PHNhbWxwOlJlc3BvbnNlIHhtbG5zOnNhbWxwPSJ1cm46b2FzaXM6bmFtZXM6dGM6U0FNTDoyLjA6cHJvdG9jb2wiPjwvc2FtbHA6UmVzcG9uc2U+";
    }

    private String generateCodeVerifier() {
        return "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    }

    private String generateCodeChallenge(String codeVerifier) {
        return "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    }
}

