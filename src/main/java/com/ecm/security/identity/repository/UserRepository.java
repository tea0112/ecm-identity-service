package com.ecm.security.identity.repository;

import com.ecm.security.identity.domain.Tenant;
import com.ecm.security.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entities.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * Finds a user by email within a tenant.
     */
    Optional<User> findByTenantAndEmailAndDeletedAtIsNull(Tenant tenant, String email);
    
    /**
     * Finds a user by username within a tenant.
     */
    Optional<User> findByTenantAndUsernameAndDeletedAtIsNull(Tenant tenant, String username);
    
    /**
     * Finds all active users for a tenant.
     */
    List<User> findByTenantAndStatusAndDeletedAtIsNull(Tenant tenant, User.UserStatus status);
    
    /**
     * Finds users by email pattern across tenants (admin function).
     */
    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', :pattern, '%')) " +
           "AND u.deletedAt IS NULL")
    List<User> findByEmailContainingIgnoreCase(@Param("pattern") String pattern);
    
    /**
     * Finds users requiring parental consent.
     */
    List<User> findByIsMinorTrueAndParentalConsentAtIsNullAndDeletedAtIsNull();
    
    /**
     * Finds locked users for a tenant.
     */
    @Query("SELECT u FROM User u WHERE u.tenant = :tenant " +
           "AND (u.status = 'LOCKED' OR u.lockedUntil > CURRENT_TIMESTAMP) " +
           "AND u.deletedAt IS NULL")
    List<User> findLockedUsers(@Param("tenant") Tenant tenant);
    
    /**
     * Finds users with failed login attempts exceeding threshold.
     */
    @Query("SELECT u FROM User u WHERE u.tenant = :tenant " +
           "AND u.failedLoginAttempts >= :threshold " +
           "AND u.deletedAt IS NULL")
    List<User> findUsersWithFailedAttempts(@Param("tenant") Tenant tenant, @Param("threshold") Integer threshold);
    
    /**
     * Finds users without MFA enabled.
     */
    List<User> findByTenantAndMfaEnabledFalseAndStatusAndDeletedAtIsNull(
        Tenant tenant, User.UserStatus status);
    
    /**
     * Finds users with unverified email addresses.
     */
    List<User> findByTenantAndEmailVerifiedFalseAndStatusAndDeletedAtIsNull(
        Tenant tenant, User.UserStatus status);
    
    /**
     * Finds inactive users (haven't logged in for specified period).
     */
    @Query("SELECT u FROM User u WHERE u.tenant = :tenant " +
           "AND u.lastLoginAt < :threshold " +
           "AND u.deletedAt IS NULL")
    List<User> findInactiveUsers(@Param("tenant") Tenant tenant, @Param("threshold") Instant threshold);
    
    /**
     * Counts active users for a tenant.
     */
    long countByTenantAndStatusAndDeletedAtIsNull(Tenant tenant, User.UserStatus status);
    
    /**
     * Finds users pending deletion.
     */
    List<User> findByStatusAndDeletedAtIsNotNull(User.UserStatus status);
    
    /**
     * Finds users by age range (for compliance).
     */
    @Query("SELECT u FROM User u WHERE u.tenant = :tenant " +
           "AND u.dateOfBirth BETWEEN :startDate AND :endDate " +
           "AND u.deletedAt IS NULL")
    List<User> findUsersByAgeRange(
        @Param("tenant") Tenant tenant,
        @Param("startDate") java.time.LocalDate startDate,
        @Param("endDate") java.time.LocalDate endDate);
}
