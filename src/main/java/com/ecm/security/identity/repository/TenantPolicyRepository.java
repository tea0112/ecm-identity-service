package com.ecm.security.identity.repository;

import com.ecm.security.identity.domain.TenantPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for TenantPolicy entities.
 */
@Repository
public interface TenantPolicyRepository extends JpaRepository<TenantPolicy, UUID> {
    
    /**
     * Finds active policies for a tenant ordered by priority.
     */
    List<TenantPolicy> findByTenantIdAndStatusOrderByPriority(UUID tenantId, TenantPolicy.PolicyStatus status);
    
    /**
     * Finds policies by type for a tenant.
     */
    List<TenantPolicy> findByTenantIdAndPolicyTypeAndStatus(UUID tenantId, TenantPolicy.PolicyType policyType, TenantPolicy.PolicyStatus status);
    
    /**
     * Finds applicable policies for authorization evaluation.
     * This is a complex query that checks subjects, resources, and actions arrays.
     */
    @Query("SELECT p FROM TenantPolicy p WHERE p.tenant.id = :tenantId " +
           "AND p.status = 'ACTIVE' AND p.deletedAt IS NULL " +
           "AND (p.subjects IS NULL OR :subject = ANY(p.subjects) OR '*' = ANY(p.subjects)) " +
           "AND (p.resources IS NULL OR :resource = ANY(p.resources) OR '*' = ANY(p.resources)) " +
           "AND (p.actions IS NULL OR :action = ANY(p.actions) OR '*' = ANY(p.actions)) " +
           "ORDER BY p.priority ASC")
    List<TenantPolicy> findApplicablePolicies(
        @Param("tenantId") UUID tenantId,
        @Param("subject") String subject,
        @Param("resource") String resource,
        @Param("action") String action
    );
    
    /**
     * Finds policies that require MFA for a tenant.
     */
    List<TenantPolicy> findByTenantIdAndMfaRequiredTrueAndStatus(UUID tenantId, TenantPolicy.PolicyStatus status);
    
    /**
     * Finds policies that require step-up authentication for a tenant.
     */
    List<TenantPolicy> findByTenantIdAndStepUpRequiredTrueAndStatus(UUID tenantId, TenantPolicy.PolicyStatus status);
    
    /**
     * Finds break-glass eligible policies for a tenant.
     */
    List<TenantPolicy> findByTenantIdAndBreakGlassEligibleTrueAndStatus(UUID tenantId, TenantPolicy.PolicyStatus status);
    
    /**
     * Finds policies by name pattern for a tenant.
     */
    @Query("SELECT p FROM TenantPolicy p WHERE p.tenant.id = :tenantId " +
           "AND LOWER(p.name) LIKE LOWER(CONCAT('%', :pattern, '%')) " +
           "AND p.deletedAt IS NULL")
    List<TenantPolicy> findByTenantIdAndNameContainingIgnoreCase(@Param("tenantId") UUID tenantId, @Param("pattern") String pattern);
    
    /**
     * Finds policies with specific tags.
     */
    @Query("SELECT p FROM TenantPolicy p WHERE p.tenant.id = :tenantId " +
           "AND :tag = ANY(p.tags) AND p.deletedAt IS NULL")
    List<TenantPolicy> findByTenantIdAndTag(@Param("tenantId") UUID tenantId, @Param("tag") String tag);
    
    /**
     * Counts active policies for a tenant.
     */
    long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, TenantPolicy.PolicyStatus status);
    
    /**
     * Finds policies that need review (e.g., old policies without recent updates).
     */
    @Query("SELECT p FROM TenantPolicy p WHERE p.tenant.id = :tenantId " +
           "AND p.status = 'ACTIVE' AND p.deletedAt IS NULL " +
           "AND p.updatedAt < CURRENT_TIMESTAMP - INTERVAL '180 days'")
    List<TenantPolicy> findPoliciesNeedingReview(@Param("tenantId") UUID tenantId);
}
