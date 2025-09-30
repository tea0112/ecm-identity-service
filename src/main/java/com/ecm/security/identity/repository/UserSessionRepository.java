package com.ecm.security.identity.repository;

import com.ecm.security.identity.domain.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for UserSession entities.
 */
@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    
    /**
     * Finds a session by session ID.
     */
    Optional<UserSession> findBySessionId(String sessionId);
    
    /**
     * Finds an active session by session ID and status.
     */
    Optional<UserSession> findBySessionIdAndStatus(String sessionId, UserSession.SessionStatus status);
    
    /**
     * Finds all active sessions for a user.
     */
    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId " +
           "AND s.status = 'ACTIVE' AND s.expiresAt > CURRENT_TIMESTAMP " +
           "ORDER BY s.lastActivityAt DESC")
    List<UserSession> findActiveSessionsByUserId(@Param("userId") UUID userId);
    
    /**
     * Finds sessions by status.
     */
    List<UserSession> findByStatus(UserSession.SessionStatus status);
    
    /**
     * Finds sessions by risk level and status.
     */
    List<UserSession> findByRiskLevelAndStatus(UserSession.RiskLevel riskLevel, UserSession.SessionStatus status);
    
    /**
     * Finds expired sessions.
     */
    @Query("SELECT s FROM UserSession s WHERE s.expiresAt < :now " +
           "AND s.status = 'ACTIVE'")
    List<UserSession> findExpiredSessions(@Param("now") Instant now);
    
    /**
     * Finds sessions for a user within a date range.
     */
    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId " +
           "AND s.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY s.createdAt DESC")
    List<UserSession> findUserSessionsInDateRange(
        @Param("userId") UUID userId,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );
    
    /**
     * Finds sessions by device.
     */
    List<UserSession> findByDeviceIdOrderByCreatedAtDesc(UUID deviceId);
    
    /**
     * Finds sessions with impossible travel detected.
     */
    List<UserSession> findByImpossibleTravelDetectedTrueAndStatus(UserSession.SessionStatus status);
    
    /**
     * Finds sessions that require step-up authentication.
     */
    @Query("SELECT s FROM UserSession s WHERE s.stepUpRequiredFor IS NOT NULL " +
           "AND s.stepUpCompleted = false AND s.status = 'ACTIVE'")
    List<UserSession> findSessionsRequiringStepUp();
    
    /**
     * Finds concurrent sessions for a user from different locations.
     */
    @Query("SELECT s FROM UserSession s WHERE s.user.id = :userId " +
           "AND s.status = 'ACTIVE' AND s.expiresAt > CURRENT_TIMESTAMP " +
           "AND s.locationCountry != :currentCountry")
    List<UserSession> findConcurrentSessionsFromDifferentCountries(
        @Param("userId") UUID userId,
        @Param("currentCountry") String currentCountry
    );
    
    /**
     * Counts active sessions for a user.
     */
    @Query("SELECT COUNT(s) FROM UserSession s WHERE s.user.id = :userId " +
           "AND s.status = 'ACTIVE' AND s.expiresAt > CURRENT_TIMESTAMP")
    long countActiveSessionsByUserId(@Param("userId") UUID userId);
    
    /**
     * Finds sessions by refresh token family (for rotation detection).
     */
    List<UserSession> findByRefreshTokenFamily(String refreshTokenFamily);
    
    /**
     * Finds sessions that haven't been active for a specified period.
     */
    @Query("SELECT s FROM UserSession s WHERE s.lastActivityAt < :threshold " +
           "AND s.status = 'ACTIVE'")
    List<UserSession> findInactiveSessions(@Param("threshold") Instant threshold);
    
    /**
     * Finds sessions by client application.
     */
    List<UserSession> findByClientAppIdAndStatusOrderByCreatedAtDesc(
        String clientAppId, UserSession.SessionStatus status);
    
    /**
     * Finds high-risk sessions for security monitoring.
     */
    @Query("SELECT s FROM UserSession s WHERE s.riskScore >= :minRiskScore " +
           "AND s.status = 'ACTIVE' ORDER BY s.riskScore DESC")
    List<UserSession> findHighRiskSessions(@Param("minRiskScore") Double minRiskScore);
    
    /**
     * Finds sessions by IP address pattern for security analysis.
     */
    @Query("SELECT s FROM UserSession s WHERE s.ipAddress LIKE :ipPattern " +
           "AND s.createdAt >= :since ORDER BY s.createdAt DESC")
    List<UserSession> findSessionsByIpPattern(
        @Param("ipPattern") String ipPattern,
        @Param("since") Instant since
    );
    
    /**
     * Finds sessions that need security review.
     */
    @Query("SELECT s FROM UserSession s WHERE s.status = 'ACTIVE' " +
           "AND (s.riskScore >= 70 OR s.impossibleTravelDetected = true " +
           "OR s.deviceFingerprintChanged = true) " +
           "ORDER BY s.riskScore DESC")
    List<UserSession> findSessionsNeedingSecurityReview();
}
