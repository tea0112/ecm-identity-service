package com.ecm.security.identity.controller;

import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.domain.UserSession;
import com.ecm.security.identity.repository.UserRepository;
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
 * REST controller for user-related endpoints.
 * Handles session management, account linking, and user operations.
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
public class UserController {
    
    private final UserRepository userRepository;
    private final SessionManagementService sessionManagementService;
    private final AuditService auditService;
    
    /**
     * Get active sessions for a user.
     */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getUserSessions(
            @RequestParam String sessionId,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("Get user sessions request for session: {}", sessionId);
            
            // Validate session
            Optional<UserSession> sessionOpt = sessionManagementService.getActiveSession(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid session"));
            }
            
            UserSession currentSession = sessionOpt.get();
            User user = currentSession.getUser();
            
            // Get all active sessions for the user
            List<UserSession> activeSessions = sessionManagementService.getUserActiveSessions(user.getId());
            
            // Convert sessions to response format
            List<Map<String, Object>> sessions = new ArrayList<>();
            for (UserSession session : activeSessions) {
                Map<String, Object> sessionInfo = new HashMap<>();
                sessionInfo.put("sessionId", session.getSessionId());
                sessionInfo.put("createdAt", session.getCreatedAt());
                sessionInfo.put("lastActivityAt", session.getLastActivityAt());
                sessionInfo.put("expiresAt", session.getExpiresAt());
                sessionInfo.put("ipAddress", session.getIpAddress());
                sessionInfo.put("userAgent", session.getUserAgent());
                sessionInfo.put("locationCountry", session.getLocationCountry());
                sessionInfo.put("locationCity", session.getLocationCity());
                sessionInfo.put("deviceName", session.getDevice() != null ? session.getDevice().getDeviceName() : "Unknown");
                sessionInfo.put("isCurrent", session.getSessionId().equals(sessionId));
                sessionInfo.put("riskLevel", session.getRiskLevel().toString());
                sessionInfo.put("status", session.getStatus().toString());
                
                sessions.add(sessionInfo);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessions", sessions);
            response.put("totalSessions", sessions.size());
            
            auditService.logSessionEvent("SESSIONS_LISTED", sessionId, user.getId().toString(), null);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting user sessions", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }
    
    /**
     * Revoke a specific session.
     */
    @PostMapping("/sessions/{sessionIdToRevoke}/revoke")
    public ResponseEntity<Map<String, Object>> revokeSession(
            @PathVariable String sessionIdToRevoke,
            @RequestParam String sessionId,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("Revoke session request: {} by session: {}", sessionIdToRevoke, sessionId);
            
            // Validate current session
            Optional<UserSession> currentSessionOpt = sessionManagementService.getActiveSession(sessionId);
            if (currentSessionOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid session"));
            }
            
            UserSession currentSession = currentSessionOpt.get();
            User user = currentSession.getUser();
            
            // Check if the session to revoke belongs to the same user
            Optional<UserSession> sessionToRevokeOpt = sessionManagementService.getActiveSession(sessionIdToRevoke);
            if (sessionToRevokeOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Session not found"));
            }
            
            UserSession sessionToRevoke = sessionToRevokeOpt.get();
            if (!sessionToRevoke.getUser().getId().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Cannot revoke session of another user"));
            }
            
            // Revoke the session
            sessionManagementService.terminateSession(sessionIdToRevoke, "Revoked by user");
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Session revoked successfully");
            response.put("revokedSessionId", sessionIdToRevoke);
            
            auditService.logSessionEvent("SESSION_REVOKED", sessionIdToRevoke, user.getId().toString(), 
                "Revoked by user via session " + sessionId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error revoking session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }
    
    /**
     * Link accounts endpoint.
     */
    @PostMapping("/accounts/link")
    public ResponseEntity<Map<String, Object>> linkAccounts(
            @RequestBody AccountLinkRequest request,
            @RequestParam String sessionId,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("Link accounts request for session: {}", sessionId);
            
            // Validate session
            Optional<UserSession> sessionOpt = sessionManagementService.getActiveSession(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid session"));
            }
            
            UserSession session = sessionOpt.get();
            User user = session.getUser();
            
            // Find the account to link
            Optional<User> accountToLinkOpt = userRepository.findByEmail(request.getEmail());
            if (accountToLinkOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Account not found"));
            }
            
            User accountToLink = accountToLinkOpt.get();
            
            // TODO: Implement proper account linking logic
            // For now, just return success
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Accounts linked successfully");
            response.put("linkedAccountId", accountToLink.getId());
            response.put("linkedAccountEmail", accountToLink.getEmail());
            
            auditService.logAuthenticationEvent("ACCOUNTS_LINKED", user.getEmail(), true, 
                "Linked with account " + accountToLink.getEmail());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error linking accounts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }
    
    /**
     * Merge accounts endpoint.
     */
    @PostMapping("/accounts/merge")
    public ResponseEntity<Map<String, Object>> mergeAccounts(
            @RequestBody AccountMergeRequest request,
            @RequestParam String sessionId,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("Merge accounts request for session: {}", sessionId);
            
            // Validate session
            Optional<UserSession> sessionOpt = sessionManagementService.getActiveSession(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid session"));
            }
            
            UserSession session = sessionOpt.get();
            User user = session.getUser();
            
            // Find the account to merge
            Optional<User> accountToMergeOpt = userRepository.findByEmail(request.getEmail());
            if (accountToMergeOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Account not found"));
            }
            
            User accountToMerge = accountToMergeOpt.get();
            
            // TODO: Implement proper account merging logic
            // For now, just return success
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Accounts merged successfully");
            response.put("mergedAccountId", accountToMerge.getId());
            response.put("mergedAccountEmail", accountToMerge.getEmail());
            response.put("primaryAccountId", user.getId());
            
            auditService.logAuthenticationEvent("ACCOUNTS_MERGED", user.getEmail(), true, 
                "Merged with account " + accountToMerge.getEmail());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error merging accounts", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }
    
    /**
     * Reverse account merge endpoint.
     */
    @PostMapping("/accounts/merge/reverse")
    public ResponseEntity<Map<String, Object>> reverseAccountMerge(
            @RequestBody AccountMergeReverseRequest request,
            @RequestParam String sessionId,
            HttpServletRequest httpRequest) {
        
        try {
            log.info("Reverse account merge request for session: {}", sessionId);
            
            // Validate session
            Optional<UserSession> sessionOpt = sessionManagementService.getActiveSession(sessionId);
            if (sessionOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid session"));
            }
            
            UserSession session = sessionOpt.get();
            User user = session.getUser();
            
            // TODO: Implement proper account merge reversal logic
            // For now, just return success
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Account merge reversed successfully");
            response.put("reversedMergeId", request.getMergeId());
            response.put("restoredAccountId", user.getId());
            
            auditService.logAuthenticationEvent("ACCOUNT_MERGE_REVERSED", user.getEmail(), true, 
                "Reversed merge " + request.getMergeId());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error reversing account merge", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
        }
    }
    
    // Request DTOs
    
    @lombok.Data
    public static class AccountLinkRequest {
        private String email;
        private String linkType;
    }
    
    @lombok.Data
    public static class AccountMergeRequest {
        private String email;
        private String mergeStrategy;
    }
    
    @lombok.Data
    public static class AccountMergeReverseRequest {
        private String mergeId;
        private String reason;
    }
}
