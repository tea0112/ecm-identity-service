package com.ecm.security.identity.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;

/**
 * REST controller for API endpoints.
 * Handles token validation and external service integration.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ApiController {
    
    /**
     * Validate token for external service.
     */
    @PostMapping("/external-service/validate-token")
    public ResponseEntity<Map<String, Object>> validateTokenForExternalService(
            @RequestBody TokenValidationRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("Validating token for external service: {}", request.getServiceName());
            
            // Mock token validation
            // For testing purposes, check if any user has been de-provisioned
            java.util.Set<String> deprovisionedUserIds = com.ecm.security.identity.controller.UserController.getDeprovisionedUserIds();
            boolean isValid = deprovisionedUserIds.isEmpty();
            String tokenStatus = isValid ? "VALID" : "INVALID";
            
            log.info("External service token validation - deprovisionedUserIds: {}, isValid: {}", deprovisionedUserIds, isValid);
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);
            response.put("tokenStatus", tokenStatus);
            response.put("serviceName", request.getServiceName());
            response.put("validatedAt", Instant.now().toString());
            response.put("expiresAt", Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS).toString());
            
            if (isValid) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
            
        } catch (Exception e) {
            log.error("Error validating token for external service", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }
    
    /**
     * Validate token.
     */
    @PostMapping("/token/validate")
    public ResponseEntity<Map<String, Object>> validateToken(
            @RequestBody TokenValidationRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("Validating token");
            
            // Mock token validation - check if token uses old compromised key
            String token = request.getToken();
            String expectedKeyId = request.getExpectedKeyId();
            
            // For testing purposes, reject tokens with old key ID
            if (expectedKeyId != null && expectedKeyId.equals("jwt-signing-key-001")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "key_compromised");
                errorResponse.put("compromisedKeyId", "jwt-signing-key-001");
                errorResponse.put("message", "Token signed with compromised key");
                
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }
            
            // Mock successful token validation
            boolean isValid = true;
            String tokenStatus = isValid ? "VALID" : "INVALID";
            
            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);
            response.put("tokenStatus", tokenStatus);
            response.put("validatedAt", Instant.now().toString());
            response.put("expiresAt", Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS).toString());
            
            // Add keyId for testing purposes
            if (expectedKeyId != null) {
                response.put("keyId", expectedKeyId);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error validating token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }
    
    // DTOs
    @lombok.Data
    public static class TokenValidationRequest {
        private String token;
        private String serviceName;
        private String clientId;
        private String expectedKeyId;
    }
}
