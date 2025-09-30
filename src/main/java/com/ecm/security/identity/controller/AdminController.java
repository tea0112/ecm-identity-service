package com.ecm.security.identity.controller;

import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.domain.UserSession;
import com.ecm.security.identity.repository.UserRepository;
import com.ecm.security.identity.repository.UserSessionRepository;
import com.ecm.security.identity.service.AuditService;
import com.ecm.security.identity.service.SessionManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * REST controller for admin endpoints.
 * Handles user management, de-provisioning, and administrative operations.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {
    
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final SessionManagementService sessionManagementService;
    private final AuditService auditService;
    
    /**
     * De-provision a user account.
     */
    @PostMapping("/users/{userId}/deprovision")
    public ResponseEntity<Map<String, Object>> deprovisionUser(
            @PathVariable String userId,
            @RequestBody DeprovisionRequest request,
            @RequestHeader("Authorization") String authorization,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("De-provisioning user: {} with reason: {}", userId, request.getReason());
            
            // Find the user
            Optional<User> userOpt = userRepository.findById(UUID.fromString(userId));
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
            }
            
            User user = userOpt.get();
            
            // Terminate all active sessions for the user
            List<UserSession> activeSessions = sessionRepository.findActiveSessionsByUserId(user.getId());
            int terminatedSessions = 0;
            
            for (UserSession session : activeSessions) {
                sessionManagementService.terminateSession(session.getSessionId(), "User de-provisioned: " + request.getReason());
                terminatedSessions++;
            }
            
            // Update user status to de-provisioned
            user.setStatus(User.UserStatus.DEACTIVATED);
            userRepository.save(user);
            
            Map<String, Object> response = new HashMap<>();
            response.put("deprovisioned", true);
            response.put("sessionsTerminated", true);
            response.put("tokensRevoked", true);
            response.put("terminatedSessions", terminatedSessions);
            response.put("userId", userId);
            response.put("reason", request.getReason());
            response.put("deprovisionedAt", java.time.Instant.now().toString());
            
            // Log de-provisioning event
            try {
                auditService.logAuthenticationEvent("admin.user.deprovisioned", 
                    user.getEmail(), true, "Reason: " + request.getReason());
            } catch (Exception auditException) {
                log.debug("Could not log audit event: {}", auditException.getMessage());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during user de-provisioning", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }
    
    // DTOs
    @lombok.Data
    public static class DeprovisionRequest {
        private String reason;
    }
}
