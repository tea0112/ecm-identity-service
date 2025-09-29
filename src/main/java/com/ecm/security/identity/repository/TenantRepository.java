package com.ecm.security.identity.repository;

import com.ecm.security.identity.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Tenant entities.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    
    /**
     * Finds a tenant by tenant code, excluding archived tenants.
     */
    Optional<Tenant> findByTenantCodeAndStatusNot(String tenantCode, Tenant.TenantStatus status);
    
    /**
     * Finds a tenant by domain, excluding archived tenants.
     */
    Optional<Tenant> findByDomainAndStatusNot(String domain, Tenant.TenantStatus status);
    
    /**
     * Finds all active tenants.
     */
    List<Tenant> findByStatusAndDeletedAtIsNull(Tenant.TenantStatus status);
    
    /**
     * Finds tenants by subscription tier.
     */
    List<Tenant> findBySubscriptionTierAndStatusAndDeletedAtIsNull(String subscriptionTier, Tenant.TenantStatus status);
    
    /**
     * Checks if a tenant code is available.
     */
    boolean existsByTenantCodeAndDeletedAtIsNull(String tenantCode);
    
    /**
     * Checks if a domain is available.
     */
    boolean existsByDomainAndDeletedAtIsNull(String domain);
    
    /**
     * Finds tenants that need suspension (e.g., exceeded user limits).
     */
    @Query("SELECT t FROM Tenant t WHERE t.status = 'ACTIVE' AND t.maxUsers IS NOT NULL AND " +
           "(SELECT COUNT(u) FROM User u WHERE u.tenant = t AND u.status = 'ACTIVE' AND u.deletedAt IS NULL) > t.maxUsers")
    List<Tenant> findTenantsExceedingUserLimits();
    
    /**
     * Finds tenants by name pattern.
     */
    @Query("SELECT t FROM Tenant t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :pattern, '%')) " +
           "AND t.status != 'ARCHIVED' AND t.deletedAt IS NULL")
    List<Tenant> findByNameContainingIgnoreCase(@Param("pattern") String pattern);
}
