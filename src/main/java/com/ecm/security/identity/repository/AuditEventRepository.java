package com.ecm.security.identity.repository;

import com.ecm.security.identity.domain.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for AuditEvent entities.
 * Provides read-only access to audit events for compliance and forensic analysis.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    
    /**
     * Finds the last audit event by chain sequence for cryptographic chaining.
     */
    AuditEvent findTopByOrderByChainSequenceDesc();
    
    /**
     * Finds audit events for a specific tenant within a date range.
     */
    Page<AuditEvent> findByTenantIdAndTimestampBetweenOrderByTimestampDesc(
        UUID tenantId, Instant startTime, Instant endTime, Pageable pageable);
    
    /**
     * Finds audit events for a specific user within a date range.
     */
    Page<AuditEvent> findByUserIdAndTimestampBetweenOrderByTimestampDesc(
        UUID userId, Instant startTime, Instant endTime, Pageable pageable);
    
    /**
     * Finds audit events for a specific session.
     */
    List<AuditEvent> findBySessionIdOrderByTimestampDesc(String sessionId);
    
    /**
     * Finds audit events by event type within a date range.
     */
    Page<AuditEvent> findByEventTypeAndTimestampBetweenOrderByTimestampDesc(
        String eventType, Instant startTime, Instant endTime, Pageable pageable);
    
    /**
     * Finds audit events by severity level within a date range.
     */
    Page<AuditEvent> findBySeverityAndTimestampBetweenOrderByTimestampDesc(
        AuditEvent.Severity severity, Instant startTime, Instant endTime, Pageable pageable);
    
    /**
     * Finds high-risk audit events for security monitoring.
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.riskScore >= :minRiskScore " +
           "AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditEvent> findHighRiskEvents(@Param("minRiskScore") Double minRiskScore, @Param("since") Instant since);
    
    /**
     * Finds security incidents within a date range.
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.severity = 'SECURITY_INCIDENT' " +
           "AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<AuditEvent> findSecurityIncidents(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
    
    /**
     * Finds failed authentication attempts for security analysis.
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.eventType LIKE '%login%' " +
           "AND a.outcome = 'FAILURE' AND a.timestamp >= :since ORDER BY a.timestamp DESC")
    List<AuditEvent> findFailedAuthenticationAttempts(@Param("since") Instant since);
    
    /**
     * Finds audit events by actor (user) within a date range for forensic analysis.
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.actorId = :actorId " +
           "AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<AuditEvent> findByActorIdAndTimestampBetween(
        @Param("actorId") UUID actorId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
    
    /**
     * Finds audit events affecting a specific target (resource).
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.targetId = :targetId " +
           "AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    List<AuditEvent> findByTargetIdAndTimestampBetween(
        @Param("targetId") UUID targetId, @Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
    
    /**
     * Finds audit events for compliance reporting by tenant.
     */
    @Query(value = "SELECT a.* FROM audit_events a WHERE a.tenant_id = :tenantId " +
           "AND :complianceFlag = ANY(a.compliance_flags) " +
           "AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC", nativeQuery = true)
    List<AuditEvent> findComplianceEvents(
        @Param("tenantId") UUID tenantId, 
        @Param("complianceFlag") String complianceFlag,
        @Param("startTime") Instant startTime, 
        @Param("endTime") Instant endTime);
    
    /**
     * Finds audit events under legal hold.
     */
    List<AuditEvent> findByLegalHoldTrueOrderByTimestampDesc();
    
    /**
     * Finds audit events eligible for deletion (past retention period).
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.retentionUntil < :now " +
           "AND a.legalHold = false ORDER BY a.timestamp ASC")
    List<AuditEvent> findEventsEligibleForDeletion(@Param("now") Instant now);
    
    /**
     * Counts audit events by event type and outcome for analytics.
     */
    @Query("SELECT a.eventType, a.outcome, COUNT(a) FROM AuditEvent a " +
           "WHERE a.tenantId = :tenantId AND a.timestamp BETWEEN :startTime AND :endTime " +
           "GROUP BY a.eventType, a.outcome")
    List<Object[]> countEventsByTypeAndOutcome(
        @Param("tenantId") UUID tenantId, 
        @Param("startTime") Instant startTime, 
        @Param("endTime") Instant endTime);
    
    /**
     * Finds audit events with integrity issues (chain verification failures).
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.eventHash IS NULL " +
           "OR a.chainSequence IS NULL ORDER BY a.timestamp DESC")
    List<AuditEvent> findEventsWithIntegrityIssues();
    
    /**
     * Finds audit events by correlation ID for tracing requests.
     */
    List<AuditEvent> findByCorrelationIdOrderByTimestampAsc(String correlationId);
    
    /**
     * Searches audit events by description pattern.
     */
    @Query("SELECT a FROM AuditEvent a WHERE a.tenantId = :tenantId " +
           "AND LOWER(a.description) LIKE LOWER(CONCAT('%', :pattern, '%')) " +
           "AND a.timestamp BETWEEN :startTime AND :endTime ORDER BY a.timestamp DESC")
    Page<AuditEvent> searchByDescription(
        @Param("tenantId") UUID tenantId,
        @Param("pattern") String pattern,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime,
        Pageable pageable);
    
    /**
     * Simple method to find audit events by user ID (for tests).
     */
    List<AuditEvent> findByUserId(UUID userId);
    
    /**
     * Simple method to find audit events by tenant ID (for tests).
     */
    List<AuditEvent> findByTenantId(UUID tenantId);
    
    /**
     * Find audit events by tenant code (for tests).
     */
    @Query(value = "SELECT ae.* FROM audit_events ae JOIN tenants t ON ae.tenant_id = t.id WHERE t.tenant_code = :tenantCode", nativeQuery = true)
    List<AuditEvent> findByTenantCode(@Param("tenantCode") String tenantCode);
}
