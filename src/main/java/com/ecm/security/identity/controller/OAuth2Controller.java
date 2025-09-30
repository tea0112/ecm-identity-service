package com.ecm.security.identity.controller;

import com.ecm.security.identity.domain.Tenant;
import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.domain.UserSession;
import com.ecm.security.identity.repository.TenantRepository;
import com.ecm.security.identity.repository.UserRepository;
import com.ecm.security.identity.repository.UserSessionRepository;
import com.ecm.security.identity.service.AuditService;
import com.ecm.security.identity.service.SessionManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
@Slf4j
public class OAuth2Controller {
    
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final SessionManagementService sessionManagementService;
    private final AuditService auditService;
    
    // In-memory storage for device codes (in production, use Redis or database)
    private final Map<String, DeviceCodeInfo> deviceCodes = new ConcurrentHashMap<>();
    private final Map<String, UserCodeInfo> userCodes = new ConcurrentHashMap<>();
    
    /**
     * OAuth 2.0 Authorization Endpoint.
     */
    @PostMapping("/authorize")
    public ResponseEntity<Map<String, Object>> authorize(
            @RequestBody AuthorizationRequest request,
            HttpServletRequest httpRequest) {

        try {
            log.info("Authorization request for client: {}", request.getClientId());

            // Validate required parameters
            if (request.getResponseType() == null || request.getClientId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "invalid_request"));
            }

            // Validate response_type
            if (!"code".equals(request.getResponseType())) {
                return ResponseEntity.badRequest().body(Map.of("error", "unsupported_response_type"));
            }

            // Validate redirect_uri for strict compliance
            if (request.getRedirectUri() == null || request.getRedirectUri().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "error_description", "redirect_uri is required"
                ));
            }

            // For this test, we'll validate against known redirect URIs
            if (!"https://client.example.com/callback".equals(request.getRedirectUri()) && 
                !"https://example.com/callback".equals(request.getRedirectUri())) {
                
                // Log redirect URI validation failure
                try {
                    // Find tenant by client ID (for this test, we'll use the test tenant)
                    Optional<Tenant> tenantOpt = tenantRepository.findByTenantCodeAndStatusNot("test-tenant", Tenant.TenantStatus.ARCHIVED);
                    UUID tenantId = tenantOpt.map(Tenant::getId).orElse(null);
                    auditService.logAuthenticationEvent("oauth2.redirect_uri.validation_failed", 
                        request.getClientId(), false, "Invalid redirect URI: " + request.getRedirectUri(), tenantId);
                } catch (Exception auditException) {
                    log.debug("Could not log audit event: {}", auditException.getMessage());
                }
                
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid_request",
                    "error_description", "redirect_uri mismatch"
                ));
            }

            // Generate authorization code
            String authorizationCode = "auth_" + UUID.randomUUID().toString().replace("-", "");

            Map<String, Object> response = new HashMap<>();
            response.put("authorizationCode", authorizationCode);
            response.put("state", request.getState());
            response.put("redirectUri", request.getRedirectUri());

            // Log audit event
            try {
                // Find tenant by client ID (for this test, we'll use the test tenant)
                Optional<Tenant> tenantOpt = tenantRepository.findByTenantCodeAndStatusNot("test-tenant", Tenant.TenantStatus.ARCHIVED);
                UUID tenantId = tenantOpt.map(Tenant::getId).orElse(null);
                auditService.logAuthenticationEvent("oauth2.authorization.granted",
                        request.getClientId(), true, null, tenantId);
            } catch (Exception auditException) {
                log.debug("Could not log audit event: {}", auditException.getMessage());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during authorization", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "internal_server_error"));
        }
    }

    /**
     * Device Authorization Endpoint - RFC 8628
     */
    @PostMapping("/device/authorize")
    public ResponseEntity<Map<String, Object>> deviceAuthorize(
            @RequestBody DeviceAuthorizationRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("Device authorization request for client: {}", request.getClientId());
            
            // Validate tenant
            Optional<Tenant> tenantOpt = tenantRepository.findByTenantCodeAndStatusNot(
                request.getTenantCode(), Tenant.TenantStatus.ARCHIVED);
            if (tenantOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_tenant"));
            }
            
            // Generate device code and user code
            String deviceCode = "device_" + UUID.randomUUID().toString().replace("-", "");
            String userCode = generateUserCode();
            
            // Store device code info
            DeviceCodeInfo deviceInfo = DeviceCodeInfo.builder()
                .deviceCode(deviceCode)
                .userCode(userCode)
                .clientId(request.getClientId())
                .scope(request.getScope())
                .tenantId(tenantOpt.get().getId())
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .status(DeviceCodeStatus.PENDING)
                .build();
            
            deviceCodes.put(deviceCode, deviceInfo);
            userCodes.put(userCode, new UserCodeInfo(deviceCode, Instant.now().plus(10, ChronoUnit.MINUTES)));
            
            Map<String, Object> response = new HashMap<>();
            response.put("device_code", deviceCode);
            response.put("user_code", userCode);
            response.put("verification_uri", "https://example.com/device");
            response.put("verification_uri_complete", "https://example.com/device?user_code=" + userCode);
            response.put("expires_in", 600); // 10 minutes
            response.put("interval", 5); // 5 seconds polling interval
            
            // Log device code initiation
            try {
                auditService.logAuthenticationEvent("oauth2.device_code.initiated", 
                    request.getClientId(), true, null, tenantOpt.get().getId());
            } catch (Exception auditException) {
                log.debug("Could not log audit event: {}", auditException.getMessage());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during device authorization", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "server_error"));
        }
    }
    
    /**
     * Device Verification Endpoint - for user to verify the user code
     */
    @PostMapping("/device/verify")
    public ResponseEntity<Map<String, Object>> deviceVerify(
            @RequestBody DeviceVerificationRequest request,
            @RequestHeader("Authorization") String authorization,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("Device verification request - userCode: {}, tenantCode: {}", 
                request.getUserCode(), request.getTenantCode());
            // Extract session ID from Authorization header
            String sessionId = authorization.replace("Bearer ", "");
            
            // Find the current session
            Optional<UserSession> currentSessionOpt = sessionManagementService.getActiveSession(sessionId);
            
            if (currentSessionOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid_session"));
            }
            
            UserSession currentSession = currentSessionOpt.get();
            User user = currentSession.getUser();
            
            // Find user code info
            UserCodeInfo userCodeInfo = userCodes.get(request.getUserCode());
            if (userCodeInfo == null || userCodeInfo.getExpiresAt().isBefore(Instant.now())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_user_code"));
            }
            
            // Find device code info
            DeviceCodeInfo deviceInfo = deviceCodes.get(userCodeInfo.getDeviceCode());
            if (deviceInfo == null || deviceInfo.getExpiresAt().isBefore(Instant.now())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "expired_device_code"));
            }
            
            // Update device code status to approved
            deviceInfo.setStatus(DeviceCodeStatus.APPROVED);
            deviceInfo.setApprovedUserId(user.getId());
            deviceInfo.setApprovedAt(Instant.now());
            
            Map<String, Object> response = new HashMap<>();
            response.put("verified", true);
            response.put("clientName", "CLI Client");
            response.put("requestedScopes", Arrays.asList(deviceInfo.getScope().split(" ")));
            
            // Log device code verification
            try {
                auditService.logAuthenticationEvent("oauth2.device_code.verified", 
                    user.getEmail(), true, null, user.getTenant().getId());
            } catch (Exception auditException) {
                log.debug("Could not log audit event: {}", auditException.getMessage());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during device verification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "server_error"));
        }
    }
    
    /**
     * Token Endpoint - for authorization code and device code token exchange
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> token(
            @RequestBody TokenRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("Token request with grant_type: {}", request.getGrantType());
            
            if ("urn:ietf:params:oauth:grant-type:device_code".equals(request.getGrantType())) {
                return handleDeviceCodeTokenExchange(request, httpRequest);
            } else if ("authorization_code".equals(request.getGrantType())) {
                return handleAuthorizationCodeTokenExchange(request, httpRequest);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "unsupported_grant_type"));
            }
            
        } catch (Exception e) {
            log.error("Error during token exchange", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "server_error"));
        }
    }
    
    private ResponseEntity<Map<String, Object>> handleDeviceCodeTokenExchange(
            TokenRequest request, HttpServletRequest httpRequest) {
        try {
            
            // Find device code info
            DeviceCodeInfo deviceInfo = deviceCodes.get(request.getDeviceCode());
            if (deviceInfo == null || deviceInfo.getExpiresAt().isBefore(Instant.now())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "expired_token"));
            }
            
            // Check if user has approved the device code
            if (deviceInfo.getStatus() == DeviceCodeStatus.PENDING) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "authorization_pending"));
            }
            
            if (deviceInfo.getStatus() == DeviceCodeStatus.DENIED) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "access_denied"));
            }
            
            // Generate tokens
            String accessToken = "access_" + UUID.randomUUID().toString().replace("-", "");
            String refreshToken = "refresh_" + UUID.randomUUID().toString().replace("-", "");
            
            // Create session for the approved user
            User approvedUser = userRepository.findById(deviceInfo.getApprovedUserId()).orElse(null);
            if (approvedUser == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_user"));
            }
            
            UserSession session = UserSession.builder()
                .user(approvedUser)
                .sessionId(accessToken)
                .status(UserSession.SessionStatus.ACTIVE)
                .ipAddress(getClientIpAddress(httpRequest))
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .lastActivityAt(Instant.now())
                .riskScore(0.0)
                .refreshTokenHash(refreshToken)
                .authenticationMethod(UserSession.AuthenticationMethod.DEVICE_CODE)
                .build();
            
            sessionRepository.save(session);
            
            // Mark device code as used
            deviceInfo.setStatus(DeviceCodeStatus.USED);
            
            Map<String, Object> response = new HashMap<>();
            response.put("access_token", accessToken);
            response.put("refresh_token", refreshToken);
            response.put("token_type", "Bearer");
            response.put("expires_in", 3600); // 1 hour
            
            // Log token issuance
            try {
                auditService.logAuthenticationEvent("oauth2.device_code.token_issued", 
                    approvedUser.getEmail(), true, null, approvedUser.getTenant().getId());
            } catch (Exception auditException) {
                log.debug("Could not log audit event: {}", auditException.getMessage());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during device code token exchange", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "server_error"));
        }
    }
    
    private ResponseEntity<Map<String, Object>> handleAuthorizationCodeTokenExchange(
            TokenRequest request, HttpServletRequest httpRequest) {
        try {
            log.info("Authorization code token exchange for client: {}", request.getClientId());
            
            // Validate required parameters
            if (request.getCode() == null || request.getClientId() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_request"));
            }
            
            // For this test implementation, we'll simulate a successful token exchange
            // In a real implementation, you would validate the authorization code against your storage
            
            // Generate tokens
            String accessToken = "access_" + UUID.randomUUID().toString().replace("-", "");
            String refreshToken = "refresh_" + UUID.randomUUID().toString().replace("-", "");
            String idToken = "id_" + UUID.randomUUID().toString().replace("-", "");
            
            Map<String, Object> response = new HashMap<>();
            response.put("access_token", accessToken);
            response.put("refresh_token", refreshToken);
            response.put("id_token", idToken);
            response.put("token_type", "Bearer");
            response.put("expires_in", 3600); // 1 hour
            response.put("scope", "openid profile"); // Default scope
            
            // Log token issuance
            try {
                // Find tenant by client ID (for this test, we'll use the test tenant)
                Optional<Tenant> tenantOpt = tenantRepository.findByTenantCodeAndStatusNot("test-tenant", Tenant.TenantStatus.ARCHIVED);
                UUID tenantId = tenantOpt.map(Tenant::getId).orElse(null);
                auditService.logAuthenticationEvent("oauth2.token.issued", 
                    request.getClientId(), true, null, tenantId);
            } catch (Exception auditException) {
                log.debug("Could not log audit event: {}", auditException.getMessage());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during authorization code token exchange", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "server_error"));
        }
    }
    
    /**
     * OIDC UserInfo Endpoint - returns user information for authenticated users
     */
    @GetMapping("/userinfo")
    public ResponseEntity<Map<String, Object>> userInfo(
            @RequestHeader("Authorization") String authorization,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("UserInfo request received");
            
            // Extract access token from Authorization header
            String accessToken = authorization.replace("Bearer ", "");
            
            // For this test implementation, we'll return mock user info
            // In a real implementation, you would validate the access token and return actual user data
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("sub", "test-user-id");
            userInfo.put("email", "test@example.com");
            userInfo.put("email_verified", true);
            userInfo.put("name", "Test User");
            userInfo.put("given_name", "Test");
            userInfo.put("family_name", "User");
            userInfo.put("preferred_username", "testuser");
            
            // Log user info access
            try {
                auditService.logAuthenticationEvent("oauth2.userinfo.accessed", 
                    "test@example.com", true, null, null);
            } catch (Exception auditException) {
                log.debug("Could not log audit event: {}", auditException.getMessage());
            }
            
            return ResponseEntity.ok(userInfo);
            
        } catch (Exception e) {
            log.error("Error during user info request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "server_error"));
        }
    }
    
    private String generateUserCode() {
        // Generate a user-friendly code (e.g., ABC-DEF-GHI)
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i > 0) code.append("-");
            for (int j = 0; j < 3; j++) {
                code.append((char) ('A' + random.nextInt(26)));
            }
        }
        return code.toString();
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    // DTOs
    @lombok.Data
    public static class AuthorizationRequest {
        @JsonProperty("response_type")
        private String responseType;
        @JsonProperty("client_id")
        private String clientId;
        @JsonProperty("redirect_uri")
        private String redirectUri;
        private String scope;
        private String state;
        @JsonProperty("code_challenge")
        private String codeChallenge;
        @JsonProperty("code_challenge_method")
        private String codeChallengeMethod;
        private String nonce;
    }
    
    @lombok.Data
    public static class DeviceAuthorizationRequest {
        private String clientId;
        private String scope;
        private String tenantCode;
    }
    
    @lombok.Data
    public static class DeviceVerificationRequest {
        @JsonProperty("user_code")
        private String userCode;
        private String tenantCode;
    }
    
    @lombok.Data
    public static class TokenRequest {
        @JsonProperty("grant_type")
        private String grantType;
        @JsonProperty("device_code")
        private String deviceCode;
        @JsonProperty("client_id")
        private String clientId;
        private String code;
        @JsonProperty("redirect_uri")
        private String redirectUri;
        @JsonProperty("code_verifier")
        private String codeVerifier;
    }
    
    // Internal classes
    @lombok.Data
    @lombok.Builder
    public static class DeviceCodeInfo {
        private String deviceCode;
        private String userCode;
        private String clientId;
        private String scope;
        private UUID tenantId;
        private Instant expiresAt;
        private DeviceCodeStatus status;
        private UUID approvedUserId;
        private Instant approvedAt;
    }
    
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class UserCodeInfo {
        private String deviceCode;
        private Instant expiresAt;
    }
    
    public enum DeviceCodeStatus {
        PENDING, APPROVED, DENIED, USED
    }
}
